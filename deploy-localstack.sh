#!/usr/bin/env bash
set -euo pipefail

export PATH="$PATH:/Users/saifz7/Library/Python/3.9/bin"

CART_DIR="cloudcart-cart-service"
PRODUCT_DIR="cloudcart-product-catalog-java"
ORDER_DIR="cloudcart-order-service"
PAYMENT_DIR="cloudcart-payment-service"
SHIPMENT_DIR="cloudcart-shipment-service"
SEARCH_DIR="cloudcart-search-service"
AGENT_DIR="cloudcart-agent-service"
AUTH_DIR="cloudcart-auth-service"
S3_BUCKET="sid-mysourcecode"
CART_JAR="cart-service-1.0.0.jar"
PRODUCT_JAR="product-catalog-1.0.0.jar"
ORDER_JAR="order-service-1.0.0.jar"
PAYMENT_JAR="payment-service-1.0.0.jar"
SHIPMENT_JAR="shipment-service-1.0.0.jar"
SEARCH_JAR="search-service-1.0.0.jar"
AGENT_JAR="agent-service-1.0.0.jar"
AUTH_JAR="auth-service-1.0.0.jar"

# Shared HS256 secret: signed by cloudcart-auth-service, verified by cart/order/agent.
# Generate one if the caller didn't supply one, so a fresh environment just works.
JWT_SECRET="${JWT_SECRET:-$(openssl rand -hex 32)}"

echo "==> Building cart service..."
mvn -f "$CART_DIR/pom.xml" package -q -DskipTests

echo "==> Building product catalog..."
mvn -f "$PRODUCT_DIR/pom.xml" package -q -DskipTests

echo "==> Building order service..."
mvn -f "$ORDER_DIR/pom.xml" package -q -DskipTests

echo "==> Building payment service..."
mvn -f "$PAYMENT_DIR/pom.xml" package -q -DskipTests

echo "==> Building shipment service..."
mvn -f "$SHIPMENT_DIR/pom.xml" package -q -DskipTests

echo "==> Building search service..."
mvn -f "$SEARCH_DIR/pom.xml" package -q -DskipTests

echo "==> Building auth service..."
mvn -f "$AUTH_DIR/pom.xml" package -q -DskipTests

if [ -n "${GROQ_API_KEY:-}" ]; then
  echo "==> Building agent service..."
  mvn -f "$AGENT_DIR/pom.xml" package -q -DskipTests
fi

echo "==> Uploading JARs to S3..."
awslocal s3 mb "s3://$S3_BUCKET" 2>/dev/null || true
awslocal s3 cp "$CART_DIR/target/$CART_JAR"         "s3://$S3_BUCKET/$CART_JAR"
awslocal s3 cp "$PRODUCT_DIR/target/$PRODUCT_JAR"   "s3://$S3_BUCKET/$PRODUCT_JAR"
awslocal s3 cp "$ORDER_DIR/target/$ORDER_JAR"       "s3://$S3_BUCKET/$ORDER_JAR"
awslocal s3 cp "$PAYMENT_DIR/target/$PAYMENT_JAR"   "s3://$S3_BUCKET/$PAYMENT_JAR"
awslocal s3 cp "$SHIPMENT_DIR/target/$SHIPMENT_JAR" "s3://$S3_BUCKET/$SHIPMENT_JAR"
awslocal s3 cp "$SEARCH_DIR/target/$SEARCH_JAR"       "s3://$S3_BUCKET/$SEARCH_JAR"
awslocal s3 cp "$AUTH_DIR/target/$AUTH_JAR"           "s3://$S3_BUCKET/$AUTH_JAR"
if [ -n "${GROQ_API_KEY:-}" ]; then
  awslocal s3 cp "$AGENT_DIR/target/$AGENT_JAR"         "s3://$S3_BUCKET/$AGENT_JAR"
fi

cf_deploy() {
  # cloudformation deploy exits 255 when there are no changes; treat that as success
  awslocal cloudformation deploy "$@" || { [ $? -eq 255 ] && echo "  (no changes)"; }
}

echo "==> Deploying cart service stack..."
cf_deploy \
  --template-file "$CART_DIR/cloudcart-cart-template.yaml" \
  --stack-name cloudcart-cart-dev \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides "JwtSecret=${JWT_SECRET}"

echo "==> Deploying product catalog stack..."
cf_deploy \
  --template-file "$PRODUCT_DIR/cloudcart-template.yaml" \
  --stack-name cloudcart-products-dev \
  --capabilities CAPABILITY_NAMED_IAM

# Order stack must deploy before payment and shipment (both import queue ARNs and table name)
echo "==> Deploying order service stack..."
cf_deploy \
  --template-file "$ORDER_DIR/cloudcart-order-template.yaml" \
  --stack-name cloudcart-order-dev \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides "JwtSecret=${JWT_SECRET}"

echo "==> Deploying payment service stack..."
cf_deploy \
  --template-file "$PAYMENT_DIR/cloudcart-payment-template.yaml" \
  --stack-name cloudcart-payment-dev \
  --capabilities CAPABILITY_NAMED_IAM

echo "==> Deploying shipment service stack..."
cf_deploy \
  --template-file "$SHIPMENT_DIR/cloudcart-shipment-template.yaml" \
  --stack-name cloudcart-shipment-dev \
  --capabilities CAPABILITY_NAMED_IAM

echo "==> Deploying auth service stack..."
cf_deploy \
  --template-file "$AUTH_DIR/cloudcart-auth-template.yaml" \
  --stack-name cloudcart-auth-dev \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides "JwtSecret=${JWT_SECRET}"

echo "==> Enabling DynamoDB streams on ProductsTableDev (LocalStack requires CLI; CF attribute returns 'unknown')..."
awslocal dynamodb update-table \
  --table-name ProductsTableDev \
  --stream-specification StreamEnabled=true,StreamViewType=NEW_AND_OLD_IMAGES 2>/dev/null || true

echo "==> Getting ProductsTableDev stream ARN..."
STREAM_ARN=$(awslocal dynamodbstreams list-streams \
  --table-name ProductsTableDev \
  --query "Streams[0].StreamArn" \
  --output text 2>/dev/null || echo "")
echo "    Stream ARN: ${STREAM_ARN}"

if [ -z "$STREAM_ARN" ] || [ "$STREAM_ARN" = "None" ]; then
  echo "  WARNING: Could not get stream ARN; skipping search stack deploy"
else
  echo "==> Deploying search service stack..."
  cf_deploy \
    --template-file "cloudcart-search-template.yaml" \
    --stack-name cloudcart-search-dev \
    --capabilities CAPABILITY_NAMED_IAM \
    --parameter-overrides "ProductsTableStreamArn=${STREAM_ARN}"
fi

if [ -n "${GROQ_API_KEY:-}" ]; then
  echo "==> Deploying agent service stack..."
  cf_deploy \
    --template-file "$AGENT_DIR/cloudcart-agent-template.yaml" \
    --stack-name cloudcart-agent-dev \
    --capabilities CAPABILITY_NAMED_IAM \
    --parameter-overrides "GroqApiKey=${GROQ_API_KEY}" "GroqModel=${GROQ_MODEL:-openai/gpt-oss-120b}" "JwtSecret=${JWT_SECRET}"
else
  echo "  WARNING: GROQ_API_KEY not set; skipping agent service deploy (the /agent/chat route will not be wired up)"
fi

echo "==> Deploying unified gateway stack..."
cf_deploy \
  --template-file "cloudcart-gateway-template.yaml" \
  --stack-name cloudcart-gateway-dev \
  --capabilities CAPABILITY_IAM

# LocalStack doesn't always replace the AWS::ApiGateway::Deployment resource when only
# its Description/DependsOn change (real AWS does), which can leave the "dev" stage
# serving a stale snapshot that's missing brand-new routes. Force a fresh deployment
# unconditionally so newly-added routes are always live after this script runs.
GATEWAY_ID=$(awslocal cloudformation describe-stacks \
  --stack-name cloudcart-gateway-dev \
  --query "Stacks[0].Outputs[?OutputKey=='UnifiedApiInternalUrl'].OutputValue" \
  --output text | sed -E 's#.*/restapis/([^/]+)/.*#\1#')
echo "==> Forcing a fresh gateway deployment (works around a LocalStack limitation)..."
awslocal apigateway create-deployment --rest-api-id "$GATEWAY_ID" --stage-name dev >/dev/null

echo ""
echo "==> Stack outputs:"
echo "--- Cart service ---"
awslocal cloudformation describe-stacks \
  --stack-name cloudcart-cart-dev \
  --query "Stacks[0].Outputs" \
  --output table

echo "--- Product catalog ---"
awslocal cloudformation describe-stacks \
  --stack-name cloudcart-products-dev \
  --query "Stacks[0].Outputs" \
  --output table

echo "--- Order service ---"
awslocal cloudformation describe-stacks \
  --stack-name cloudcart-order-dev \
  --query "Stacks[0].Outputs" \
  --output table

echo "--- Shipment service ---"
awslocal cloudformation describe-stacks \
  --stack-name cloudcart-shipment-dev \
  --query "Stacks[0].Outputs" \
  --output table

echo "--- Search service ---"
awslocal cloudformation describe-stacks \
  --stack-name cloudcart-search-dev \
  --query "Stacks[0].Outputs" \
  --output table

echo "--- Auth service ---"
awslocal cloudformation describe-stacks \
  --stack-name cloudcart-auth-dev \
  --query "Stacks[0].Outputs" \
  --output table

echo "--- Unified gateway ---"
awslocal cloudformation describe-stacks \
  --stack-name cloudcart-gateway-dev \
  --query "Stacks[0].Outputs" \
  --output table

echo ""
echo "==> Next step: grab the UnifiedApiInternalUrl from the gateway output above and set"
echo "    NEXT_PUBLIC_UNIFIED_API_INTERNAL=http://localhost:4566/restapis/<gateway-api-id>/dev/_user_request_"
echo "    in cloudcart-frontend/.env.local, then restart the frontend dev server."
echo ""
echo "==> Accounts are now required: register via POST /auth/register {email,password}"
echo "    (or use the /register page once the frontend is running) before using cart/orders/chat."
echo ""
if [ -n "${GROQ_API_KEY:-}" ]; then
  echo "==> To start the shopping assistant's MCP server, in a separate terminal run:"
  echo "    cd cloudcart-mcp-server && UNIFIED_API_URL=http://localhost:4566/restapis/<gateway-api-id>/dev/_user_request_ mvn spring-boot:run"
  echo "    (use the same gateway API id printed in the UnifiedApiInternalUrl output above)"
  echo ""
fi
bash "$(dirname "$0")/seed-products.sh"

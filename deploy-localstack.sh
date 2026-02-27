#!/usr/bin/env bash
set -euo pipefail

export PATH="$PATH:/Users/saifz7/Library/Python/3.9/bin"

CART_DIR="cloudcart-cart-service"
PRODUCT_DIR="cloudcart-product-catalog-java"
ORDER_DIR="cloudcart-order-service"
PAYMENT_DIR="cloudcart-payment-service"
SHIPMENT_DIR="cloudcart-shipment-service"
S3_BUCKET="sid-mysourcecode"
CART_JAR="cart-service-1.0.0.jar"
PRODUCT_JAR="product-catalog-1.0.0.jar"
ORDER_JAR="order-service-1.0.0.jar"
PAYMENT_JAR="payment-service-1.0.0.jar"
SHIPMENT_JAR="shipment-service-1.0.0.jar"

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

echo "==> Uploading JARs to S3..."
awslocal s3 mb "s3://$S3_BUCKET" 2>/dev/null || true
awslocal s3 cp "$CART_DIR/target/$CART_JAR"         "s3://$S3_BUCKET/$CART_JAR"
awslocal s3 cp "$PRODUCT_DIR/target/$PRODUCT_JAR"   "s3://$S3_BUCKET/$PRODUCT_JAR"
awslocal s3 cp "$ORDER_DIR/target/$ORDER_JAR"       "s3://$S3_BUCKET/$ORDER_JAR"
awslocal s3 cp "$PAYMENT_DIR/target/$PAYMENT_JAR"   "s3://$S3_BUCKET/$PAYMENT_JAR"
awslocal s3 cp "$SHIPMENT_DIR/target/$SHIPMENT_JAR" "s3://$S3_BUCKET/$SHIPMENT_JAR"

cf_deploy() {
  # cloudformation deploy exits 255 when there are no changes; treat that as success
  awslocal cloudformation deploy "$@" || { [ $? -eq 255 ] && echo "  (no changes)"; }
}

echo "==> Deploying cart service stack..."
cf_deploy \
  --template-file "$CART_DIR/cloudcart-cart-template.yaml" \
  --stack-name cloudcart-cart-dev \
  --capabilities CAPABILITY_NAMED_IAM

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
  --capabilities CAPABILITY_NAMED_IAM

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

echo ""
echo "==> Next step: grab the Order API ID from the output above and set"
echo "    NEXT_PUBLIC_ORDER_API_INTERNAL=http://localhost:4566/restapis/<order-api-id>/dev/_user_request_"
echo "    in cloudcart-frontend/.env.local, then restart the frontend dev server."
echo ""
bash "$(dirname "$0")/seed-products.sh"

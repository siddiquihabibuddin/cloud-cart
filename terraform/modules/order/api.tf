### REST API ###
resource "aws_api_gateway_rest_api" "order" {
  name = "OrderApiDev"
}

### Resources: /orders, /orders/{orderId} ###
resource "aws_api_gateway_resource" "orders" {
  rest_api_id = aws_api_gateway_rest_api.order.id
  parent_id   = aws_api_gateway_rest_api.order.root_resource_id
  path_part   = "orders"
}

resource "aws_api_gateway_resource" "order_by_id" {
  rest_api_id = aws_api_gateway_rest_api.order.id
  parent_id   = aws_api_gateway_resource.orders.id
  path_part   = "{orderId}"
}

### Methods ###
resource "aws_api_gateway_method" "post_order" {
  rest_api_id      = aws_api_gateway_rest_api.order.id
  resource_id      = aws_api_gateway_resource.orders.id
  http_method      = "POST"
  authorization    = "NONE"
  api_key_required = true
}

resource "aws_api_gateway_integration" "post_order" {
  rest_api_id             = aws_api_gateway_rest_api.order.id
  resource_id             = aws_api_gateway_resource.orders.id
  http_method             = aws_api_gateway_method.post_order.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = aws_lambda_function.place_order.invoke_arn
}

resource "aws_api_gateway_method" "get_order" {
  rest_api_id      = aws_api_gateway_rest_api.order.id
  resource_id      = aws_api_gateway_resource.order_by_id.id
  http_method      = "GET"
  authorization    = "NONE"
  api_key_required = true
}

resource "aws_api_gateway_integration" "get_order" {
  rest_api_id             = aws_api_gateway_rest_api.order.id
  resource_id             = aws_api_gateway_resource.order_by_id.id
  http_method             = aws_api_gateway_method.get_order.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = aws_lambda_function.get_order.invoke_arn
}

resource "aws_api_gateway_method" "list_orders" {
  rest_api_id      = aws_api_gateway_rest_api.order.id
  resource_id      = aws_api_gateway_resource.orders.id
  http_method      = "GET"
  authorization    = "NONE"
  api_key_required = true
}

resource "aws_api_gateway_integration" "list_orders" {
  rest_api_id             = aws_api_gateway_rest_api.order.id
  resource_id             = aws_api_gateway_resource.orders.id
  http_method             = aws_api_gateway_method.list_orders.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = aws_lambda_function.list_orders.invoke_arn
}

### Lambda Permissions ###
resource "aws_lambda_permission" "place_order_api" {
  statement_id  = "AllowOrderApiInvokePlaceOrder"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.place_order.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.order.execution_arn}/*/*"
}

resource "aws_lambda_permission" "get_order_api" {
  statement_id  = "AllowOrderApiInvokeGetOrder"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.get_order.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.order.execution_arn}/*/*"
}

resource "aws_lambda_permission" "list_orders_api" {
  statement_id  = "AllowOrderApiInvokeListOrders"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.list_orders.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.order.execution_arn}/*/*"
}

### Deployment & Stage ###
resource "aws_api_gateway_deployment" "order" {
  rest_api_id = aws_api_gateway_rest_api.order.id

  triggers = {
    redeployment = sha1(jsonencode([
      aws_api_gateway_method.post_order,
      aws_api_gateway_method.get_order,
      aws_api_gateway_method.list_orders,
      aws_api_gateway_integration.post_order,
      aws_api_gateway_integration.get_order,
      aws_api_gateway_integration.list_orders,
    ]))
  }

  lifecycle {
    create_before_destroy = true
  }

  depends_on = [
    aws_api_gateway_integration.post_order,
    aws_api_gateway_integration.get_order,
    aws_api_gateway_integration.list_orders,
  ]
}

resource "aws_api_gateway_stage" "order" {
  rest_api_id   = aws_api_gateway_rest_api.order.id
  deployment_id = aws_api_gateway_deployment.order.id
  stage_name    = "dev"
}

### API Key Auth ###
resource "aws_api_gateway_api_key" "order" {
  name  = "OrderApiKeyDev"
  value = "cloudcart-dev-key-2024"
  enabled = true
}

resource "aws_api_gateway_usage_plan" "order" {
  name = "OrderUsagePlanDev"

  api_stages {
    api_id = aws_api_gateway_rest_api.order.id
    stage  = aws_api_gateway_stage.order.stage_name
  }

  depends_on = [aws_api_gateway_stage.order]
}

resource "aws_api_gateway_usage_plan_key" "order" {
  key_id        = aws_api_gateway_api_key.order.id
  key_type      = "API_KEY"
  usage_plan_id = aws_api_gateway_usage_plan.order.id
}

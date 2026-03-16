### REST API ###
resource "aws_api_gateway_rest_api" "cart" {
  name = "CartApiDev"
}

### Resources: /cart, /cart/{userId}, /cart/{userId}/{productId} ###
resource "aws_api_gateway_resource" "cart" {
  rest_api_id = aws_api_gateway_rest_api.cart.id
  parent_id   = aws_api_gateway_rest_api.cart.root_resource_id
  path_part   = "cart"
}

resource "aws_api_gateway_resource" "cart_user_id" {
  rest_api_id = aws_api_gateway_rest_api.cart.id
  parent_id   = aws_api_gateway_resource.cart.id
  path_part   = "{userId}"
}

resource "aws_api_gateway_resource" "cart_product_id" {
  rest_api_id = aws_api_gateway_rest_api.cart.id
  parent_id   = aws_api_gateway_resource.cart_user_id.id
  path_part   = "{productId}"
}

### Methods ###
resource "aws_api_gateway_method" "post_cart" {
  rest_api_id   = aws_api_gateway_rest_api.cart.id
  resource_id   = aws_api_gateway_resource.cart.id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "post_cart" {
  rest_api_id             = aws_api_gateway_rest_api.cart.id
  resource_id             = aws_api_gateway_resource.cart.id
  http_method             = aws_api_gateway_method.post_cart.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = aws_lambda_function.cart["AddToCartFunctionDev"].invoke_arn
}

resource "aws_api_gateway_method" "get_cart" {
  rest_api_id   = aws_api_gateway_rest_api.cart.id
  resource_id   = aws_api_gateway_resource.cart_user_id.id
  http_method   = "GET"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "get_cart" {
  rest_api_id             = aws_api_gateway_rest_api.cart.id
  resource_id             = aws_api_gateway_resource.cart_user_id.id
  http_method             = aws_api_gateway_method.get_cart.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = aws_lambda_function.cart["ViewCartFunctionDev"].invoke_arn
}

resource "aws_api_gateway_method" "delete_cart_item" {
  rest_api_id   = aws_api_gateway_rest_api.cart.id
  resource_id   = aws_api_gateway_resource.cart_product_id.id
  http_method   = "DELETE"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "delete_cart_item" {
  rest_api_id             = aws_api_gateway_rest_api.cart.id
  resource_id             = aws_api_gateway_resource.cart_product_id.id
  http_method             = aws_api_gateway_method.delete_cart_item.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = aws_lambda_function.cart["RemoveFromCartFunctionDev"].invoke_arn
}

resource "aws_api_gateway_method" "patch_cart_item" {
  rest_api_id   = aws_api_gateway_rest_api.cart.id
  resource_id   = aws_api_gateway_resource.cart_product_id.id
  http_method   = "PATCH"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "patch_cart_item" {
  rest_api_id             = aws_api_gateway_rest_api.cart.id
  resource_id             = aws_api_gateway_resource.cart_product_id.id
  http_method             = aws_api_gateway_method.patch_cart_item.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = aws_lambda_function.cart["UpdateQuantityFunctionDev"].invoke_arn
}

resource "aws_api_gateway_method" "delete_cart_user" {
  rest_api_id   = aws_api_gateway_rest_api.cart.id
  resource_id   = aws_api_gateway_resource.cart_user_id.id
  http_method   = "DELETE"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "delete_cart_user" {
  rest_api_id             = aws_api_gateway_rest_api.cart.id
  resource_id             = aws_api_gateway_resource.cart_user_id.id
  http_method             = aws_api_gateway_method.delete_cart_user.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = aws_lambda_function.cart["ClearCartFunctionDev"].invoke_arn
}

### Lambda Permissions for this API ###
resource "aws_lambda_permission" "cart_api" {
  for_each = local.cart_functions

  statement_id  = "AllowCartApiInvoke-${each.key}"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.cart[each.key].function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.cart.execution_arn}/*/*"
}

### Deployment & Stage ###
resource "aws_api_gateway_deployment" "cart" {
  rest_api_id = aws_api_gateway_rest_api.cart.id

  triggers = {
    redeployment = sha1(jsonencode([
      aws_api_gateway_method.post_cart,
      aws_api_gateway_method.get_cart,
      aws_api_gateway_method.delete_cart_item,
      aws_api_gateway_method.patch_cart_item,
      aws_api_gateway_method.delete_cart_user,
      aws_api_gateway_integration.post_cart,
      aws_api_gateway_integration.get_cart,
      aws_api_gateway_integration.delete_cart_item,
      aws_api_gateway_integration.patch_cart_item,
      aws_api_gateway_integration.delete_cart_user,
    ]))
  }

  lifecycle {
    create_before_destroy = true
  }

  depends_on = [
    aws_api_gateway_integration.post_cart,
    aws_api_gateway_integration.get_cart,
    aws_api_gateway_integration.delete_cart_item,
    aws_api_gateway_integration.patch_cart_item,
    aws_api_gateway_integration.delete_cart_user,
  ]
}

resource "aws_api_gateway_stage" "cart" {
  rest_api_id   = aws_api_gateway_rest_api.cart.id
  deployment_id = aws_api_gateway_deployment.cart.id
  stage_name    = "dev"
}

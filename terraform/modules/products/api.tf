### REST API ###
resource "aws_api_gateway_rest_api" "products" {
  name = "ProductApiDev"
}

### Resources: /products, /products/{id}, /products/{id}/stock ###
resource "aws_api_gateway_resource" "products" {
  rest_api_id = aws_api_gateway_rest_api.products.id
  parent_id   = aws_api_gateway_rest_api.products.root_resource_id
  path_part   = "products"
}

resource "aws_api_gateway_resource" "product_id" {
  rest_api_id = aws_api_gateway_rest_api.products.id
  parent_id   = aws_api_gateway_resource.products.id
  path_part   = "{id}"
}

resource "aws_api_gateway_resource" "product_stock" {
  rest_api_id = aws_api_gateway_rest_api.products.id
  parent_id   = aws_api_gateway_resource.product_id.id
  path_part   = "stock"
}

### Methods ###
resource "aws_api_gateway_method" "get_products" {
  rest_api_id   = aws_api_gateway_rest_api.products.id
  resource_id   = aws_api_gateway_resource.products.id
  http_method   = "GET"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "get_products" {
  rest_api_id             = aws_api_gateway_rest_api.products.id
  resource_id             = aws_api_gateway_resource.products.id
  http_method             = aws_api_gateway_method.get_products.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = aws_lambda_function.products["ListProductsFunctionDev"].invoke_arn
}

resource "aws_api_gateway_method" "post_products" {
  rest_api_id   = aws_api_gateway_rest_api.products.id
  resource_id   = aws_api_gateway_resource.products.id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "post_products" {
  rest_api_id             = aws_api_gateway_rest_api.products.id
  resource_id             = aws_api_gateway_resource.products.id
  http_method             = aws_api_gateway_method.post_products.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = aws_lambda_function.products["CreateProductFunctionDev"].invoke_arn
}

resource "aws_api_gateway_method" "get_product_by_id" {
  rest_api_id   = aws_api_gateway_rest_api.products.id
  resource_id   = aws_api_gateway_resource.product_id.id
  http_method   = "GET"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "get_product_by_id" {
  rest_api_id             = aws_api_gateway_rest_api.products.id
  resource_id             = aws_api_gateway_resource.product_id.id
  http_method             = aws_api_gateway_method.get_product_by_id.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = aws_lambda_function.products["GetProductFunctionDev"].invoke_arn
}

resource "aws_api_gateway_method" "patch_stock" {
  rest_api_id   = aws_api_gateway_rest_api.products.id
  resource_id   = aws_api_gateway_resource.product_stock.id
  http_method   = "PATCH"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "patch_stock" {
  rest_api_id             = aws_api_gateway_rest_api.products.id
  resource_id             = aws_api_gateway_resource.product_stock.id
  http_method             = aws_api_gateway_method.patch_stock.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = aws_lambda_function.products["UpdateStockFunctionDev"].invoke_arn
}

### Lambda Permissions for this API ###
resource "aws_lambda_permission" "products_api" {
  for_each = local.product_functions

  statement_id  = "AllowProductApiInvoke-${each.key}"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.products[each.key].function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.products.execution_arn}/*/*"
}

### Deployment & Stage ###
resource "aws_api_gateway_deployment" "products" {
  rest_api_id = aws_api_gateway_rest_api.products.id

  triggers = {
    redeployment = sha1(jsonencode([
      aws_api_gateway_method.get_products,
      aws_api_gateway_method.post_products,
      aws_api_gateway_method.get_product_by_id,
      aws_api_gateway_method.patch_stock,
      aws_api_gateway_integration.get_products,
      aws_api_gateway_integration.post_products,
      aws_api_gateway_integration.get_product_by_id,
      aws_api_gateway_integration.patch_stock,
    ]))
  }

  lifecycle {
    create_before_destroy = true
  }

  depends_on = [
    aws_api_gateway_integration.get_products,
    aws_api_gateway_integration.post_products,
    aws_api_gateway_integration.get_product_by_id,
    aws_api_gateway_integration.patch_stock,
  ]
}

resource "aws_api_gateway_stage" "products" {
  rest_api_id   = aws_api_gateway_rest_api.products.id
  deployment_id = aws_api_gateway_deployment.products.id
  stage_name    = "dev"
}

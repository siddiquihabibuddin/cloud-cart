### Methods — Products ###
resource "aws_api_gateway_method" "get_products" {
  rest_api_id   = aws_api_gateway_rest_api.unified.id
  resource_id   = aws_api_gateway_resource.products.id
  http_method   = "GET"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "get_products" {
  rest_api_id             = aws_api_gateway_rest_api.unified.id
  resource_id             = aws_api_gateway_resource.products.id
  http_method             = aws_api_gateway_method.get_products.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${var.list_products_function_arn}/invocations"
}

resource "aws_api_gateway_method" "post_products" {
  rest_api_id   = aws_api_gateway_rest_api.unified.id
  resource_id   = aws_api_gateway_resource.products.id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "post_products" {
  rest_api_id             = aws_api_gateway_rest_api.unified.id
  resource_id             = aws_api_gateway_resource.products.id
  http_method             = aws_api_gateway_method.post_products.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${var.create_product_function_arn}/invocations"
}

resource "aws_api_gateway_method" "get_product_by_id" {
  rest_api_id   = aws_api_gateway_rest_api.unified.id
  resource_id   = aws_api_gateway_resource.product_id.id
  http_method   = "GET"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "get_product_by_id" {
  rest_api_id             = aws_api_gateway_rest_api.unified.id
  resource_id             = aws_api_gateway_resource.product_id.id
  http_method             = aws_api_gateway_method.get_product_by_id.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${var.get_product_function_arn}/invocations"
}

resource "aws_api_gateway_method" "patch_stock" {
  rest_api_id   = aws_api_gateway_rest_api.unified.id
  resource_id   = aws_api_gateway_resource.product_stock.id
  http_method   = "PATCH"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "patch_stock" {
  rest_api_id             = aws_api_gateway_rest_api.unified.id
  resource_id             = aws_api_gateway_resource.product_stock.id
  http_method             = aws_api_gateway_method.patch_stock.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${var.update_stock_function_arn}/invocations"
}

### Methods — Cart ###
resource "aws_api_gateway_method" "post_cart" {
  rest_api_id   = aws_api_gateway_rest_api.unified.id
  resource_id   = aws_api_gateway_resource.cart.id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "post_cart" {
  rest_api_id             = aws_api_gateway_rest_api.unified.id
  resource_id             = aws_api_gateway_resource.cart.id
  http_method             = aws_api_gateway_method.post_cart.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${var.add_to_cart_function_arn}/invocations"
}

resource "aws_api_gateway_method" "get_cart" {
  rest_api_id   = aws_api_gateway_rest_api.unified.id
  resource_id   = aws_api_gateway_resource.cart_user_id.id
  http_method   = "GET"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "get_cart" {
  rest_api_id             = aws_api_gateway_rest_api.unified.id
  resource_id             = aws_api_gateway_resource.cart_user_id.id
  http_method             = aws_api_gateway_method.get_cart.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${var.view_cart_function_arn}/invocations"
}

resource "aws_api_gateway_method" "delete_cart_item" {
  rest_api_id   = aws_api_gateway_rest_api.unified.id
  resource_id   = aws_api_gateway_resource.cart_product_id.id
  http_method   = "DELETE"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "delete_cart_item" {
  rest_api_id             = aws_api_gateway_rest_api.unified.id
  resource_id             = aws_api_gateway_resource.cart_product_id.id
  http_method             = aws_api_gateway_method.delete_cart_item.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${var.remove_from_cart_function_arn}/invocations"
}

resource "aws_api_gateway_method" "patch_cart_item" {
  rest_api_id   = aws_api_gateway_rest_api.unified.id
  resource_id   = aws_api_gateway_resource.cart_product_id.id
  http_method   = "PATCH"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "patch_cart_item" {
  rest_api_id             = aws_api_gateway_rest_api.unified.id
  resource_id             = aws_api_gateway_resource.cart_product_id.id
  http_method             = aws_api_gateway_method.patch_cart_item.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${var.update_quantity_function_arn}/invocations"
}

resource "aws_api_gateway_method" "delete_cart_user" {
  rest_api_id   = aws_api_gateway_rest_api.unified.id
  resource_id   = aws_api_gateway_resource.cart_user_id.id
  http_method   = "DELETE"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "delete_cart_user" {
  rest_api_id             = aws_api_gateway_rest_api.unified.id
  resource_id             = aws_api_gateway_resource.cart_user_id.id
  http_method             = aws_api_gateway_method.delete_cart_user.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${var.clear_cart_function_arn}/invocations"
}

### Methods — Orders (API key required) ###
resource "aws_api_gateway_method" "post_order" {
  rest_api_id      = aws_api_gateway_rest_api.unified.id
  resource_id      = aws_api_gateway_resource.orders.id
  http_method      = "POST"
  authorization    = "NONE"
  api_key_required = true
}

resource "aws_api_gateway_integration" "post_order" {
  rest_api_id             = aws_api_gateway_rest_api.unified.id
  resource_id             = aws_api_gateway_resource.orders.id
  http_method             = aws_api_gateway_method.post_order.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${var.place_order_function_arn}/invocations"
}

resource "aws_api_gateway_method" "list_orders" {
  rest_api_id      = aws_api_gateway_rest_api.unified.id
  resource_id      = aws_api_gateway_resource.orders.id
  http_method      = "GET"
  authorization    = "NONE"
  api_key_required = true
}

resource "aws_api_gateway_integration" "list_orders" {
  rest_api_id             = aws_api_gateway_rest_api.unified.id
  resource_id             = aws_api_gateway_resource.orders.id
  http_method             = aws_api_gateway_method.list_orders.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${var.list_orders_function_arn}/invocations"
}

resource "aws_api_gateway_method" "get_order" {
  rest_api_id      = aws_api_gateway_rest_api.unified.id
  resource_id      = aws_api_gateway_resource.order_by_id.id
  http_method      = "GET"
  authorization    = "NONE"
  api_key_required = true
}

resource "aws_api_gateway_integration" "get_order" {
  rest_api_id             = aws_api_gateway_rest_api.unified.id
  resource_id             = aws_api_gateway_resource.order_by_id.id
  http_method             = aws_api_gateway_method.get_order.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${var.get_order_function_arn}/invocations"
}

### Methods — Search ###
resource "aws_api_gateway_method" "get_search" {
  rest_api_id   = aws_api_gateway_rest_api.unified.id
  resource_id   = aws_api_gateway_resource.search.id
  http_method   = "GET"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "get_search" {
  rest_api_id             = aws_api_gateway_rest_api.unified.id
  resource_id             = aws_api_gateway_resource.search.id
  http_method             = aws_api_gateway_method.get_search.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${var.search_products_function_arn}/invocations"
}

resource "aws_api_gateway_method" "post_reindex" {
  rest_api_id   = aws_api_gateway_rest_api.unified.id
  resource_id   = aws_api_gateway_resource.search_reindex.id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "post_reindex" {
  rest_api_id             = aws_api_gateway_rest_api.unified.id
  resource_id             = aws_api_gateway_resource.search_reindex.id
  http_method             = aws_api_gateway_method.post_reindex.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${var.bulk_reindex_function_arn}/invocations"
}

### Lambda Permissions — allow UnifiedApi to invoke every function ###
resource "aws_lambda_permission" "list_products" {
  statement_id  = "AllowUnifiedApiInvokeListProducts"
  action        = "lambda:InvokeFunction"
  function_name = var.list_products_function_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.unified.execution_arn}/*/*"
}

resource "aws_lambda_permission" "create_product" {
  statement_id  = "AllowUnifiedApiInvokeCreateProduct"
  action        = "lambda:InvokeFunction"
  function_name = var.create_product_function_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.unified.execution_arn}/*/*"
}

resource "aws_lambda_permission" "get_product" {
  statement_id  = "AllowUnifiedApiInvokeGetProduct"
  action        = "lambda:InvokeFunction"
  function_name = var.get_product_function_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.unified.execution_arn}/*/*"
}

resource "aws_lambda_permission" "update_stock" {
  statement_id  = "AllowUnifiedApiInvokeUpdateStock"
  action        = "lambda:InvokeFunction"
  function_name = var.update_stock_function_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.unified.execution_arn}/*/*"
}

resource "aws_lambda_permission" "add_to_cart" {
  statement_id  = "AllowUnifiedApiInvokeAddToCart"
  action        = "lambda:InvokeFunction"
  function_name = var.add_to_cart_function_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.unified.execution_arn}/*/*"
}

resource "aws_lambda_permission" "view_cart" {
  statement_id  = "AllowUnifiedApiInvokeViewCart"
  action        = "lambda:InvokeFunction"
  function_name = var.view_cart_function_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.unified.execution_arn}/*/*"
}

resource "aws_lambda_permission" "remove_from_cart" {
  statement_id  = "AllowUnifiedApiInvokeRemoveFromCart"
  action        = "lambda:InvokeFunction"
  function_name = var.remove_from_cart_function_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.unified.execution_arn}/*/*"
}

resource "aws_lambda_permission" "update_quantity" {
  statement_id  = "AllowUnifiedApiInvokeUpdateQuantity"
  action        = "lambda:InvokeFunction"
  function_name = var.update_quantity_function_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.unified.execution_arn}/*/*"
}

resource "aws_lambda_permission" "clear_cart" {
  statement_id  = "AllowUnifiedApiInvokeClearCart"
  action        = "lambda:InvokeFunction"
  function_name = var.clear_cart_function_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.unified.execution_arn}/*/*"
}

resource "aws_lambda_permission" "place_order" {
  statement_id  = "AllowUnifiedApiInvokePlaceOrder"
  action        = "lambda:InvokeFunction"
  function_name = var.place_order_function_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.unified.execution_arn}/*/*"
}

resource "aws_lambda_permission" "get_order" {
  statement_id  = "AllowUnifiedApiInvokeGetOrder"
  action        = "lambda:InvokeFunction"
  function_name = var.get_order_function_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.unified.execution_arn}/*/*"
}

resource "aws_lambda_permission" "list_orders" {
  statement_id  = "AllowUnifiedApiInvokeListOrders"
  action        = "lambda:InvokeFunction"
  function_name = var.list_orders_function_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.unified.execution_arn}/*/*"
}

resource "aws_lambda_permission" "search_products" {
  statement_id  = "AllowUnifiedApiInvokeSearchProducts"
  action        = "lambda:InvokeFunction"
  function_name = var.search_products_function_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.unified.execution_arn}/*/*"
}

resource "aws_lambda_permission" "bulk_reindex" {
  statement_id  = "AllowUnifiedApiInvokeBulkReindex"
  action        = "lambda:InvokeFunction"
  function_name = var.bulk_reindex_function_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.unified.execution_arn}/*/*"
}

### Deployment & Stage ###
resource "aws_api_gateway_deployment" "unified" {
  rest_api_id = aws_api_gateway_rest_api.unified.id

  triggers = {
    redeployment = sha1(jsonencode([
      aws_api_gateway_method.get_products,
      aws_api_gateway_method.post_products,
      aws_api_gateway_method.get_product_by_id,
      aws_api_gateway_method.patch_stock,
      aws_api_gateway_method.post_cart,
      aws_api_gateway_method.get_cart,
      aws_api_gateway_method.delete_cart_item,
      aws_api_gateway_method.patch_cart_item,
      aws_api_gateway_method.delete_cart_user,
      aws_api_gateway_method.post_order,
      aws_api_gateway_method.list_orders,
      aws_api_gateway_method.get_order,
      aws_api_gateway_method.get_search,
      aws_api_gateway_method.post_reindex,
      aws_api_gateway_integration.get_products,
      aws_api_gateway_integration.post_products,
      aws_api_gateway_integration.get_product_by_id,
      aws_api_gateway_integration.patch_stock,
      aws_api_gateway_integration.post_cart,
      aws_api_gateway_integration.get_cart,
      aws_api_gateway_integration.delete_cart_item,
      aws_api_gateway_integration.patch_cart_item,
      aws_api_gateway_integration.delete_cart_user,
      aws_api_gateway_integration.post_order,
      aws_api_gateway_integration.list_orders,
      aws_api_gateway_integration.get_order,
      aws_api_gateway_integration.get_search,
      aws_api_gateway_integration.post_reindex,
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
    aws_api_gateway_integration.post_cart,
    aws_api_gateway_integration.get_cart,
    aws_api_gateway_integration.delete_cart_item,
    aws_api_gateway_integration.patch_cart_item,
    aws_api_gateway_integration.delete_cart_user,
    aws_api_gateway_integration.post_order,
    aws_api_gateway_integration.list_orders,
    aws_api_gateway_integration.get_order,
    aws_api_gateway_integration.get_search,
    aws_api_gateway_integration.post_reindex,
  ]
}

resource "aws_api_gateway_stage" "unified" {
  rest_api_id   = aws_api_gateway_rest_api.unified.id
  deployment_id = aws_api_gateway_deployment.unified.id
  stage_name    = "dev"
}

### Usage Plan — reuses the API key from the order module ###
resource "aws_api_gateway_usage_plan" "unified" {
  name = "UnifiedUsagePlanDev"

  api_stages {
    api_id = aws_api_gateway_rest_api.unified.id
    stage  = aws_api_gateway_stage.unified.stage_name
  }

  depends_on = [aws_api_gateway_stage.unified]
}

resource "aws_api_gateway_usage_plan_key" "unified" {
  key_id        = var.order_api_key_id
  key_type      = "API_KEY"
  usage_plan_id = aws_api_gateway_usage_plan.unified.id
}

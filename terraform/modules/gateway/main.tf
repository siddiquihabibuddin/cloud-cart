### REST API ###
resource "aws_api_gateway_rest_api" "unified" {
  name = "UnifiedApiDev"
}

### Top-level resources ###
resource "aws_api_gateway_resource" "products" {
  rest_api_id = aws_api_gateway_rest_api.unified.id
  parent_id   = aws_api_gateway_rest_api.unified.root_resource_id
  path_part   = "products"
}

resource "aws_api_gateway_resource" "cart" {
  rest_api_id = aws_api_gateway_rest_api.unified.id
  parent_id   = aws_api_gateway_rest_api.unified.root_resource_id
  path_part   = "cart"
}

resource "aws_api_gateway_resource" "orders" {
  rest_api_id = aws_api_gateway_rest_api.unified.id
  parent_id   = aws_api_gateway_rest_api.unified.root_resource_id
  path_part   = "orders"
}

resource "aws_api_gateway_resource" "search" {
  rest_api_id = aws_api_gateway_rest_api.unified.id
  parent_id   = aws_api_gateway_rest_api.unified.root_resource_id
  path_part   = "search"
}

### /products/{id} and /products/{id}/stock ###
resource "aws_api_gateway_resource" "product_id" {
  rest_api_id = aws_api_gateway_rest_api.unified.id
  parent_id   = aws_api_gateway_resource.products.id
  path_part   = "{id}"
}

resource "aws_api_gateway_resource" "product_stock" {
  rest_api_id = aws_api_gateway_rest_api.unified.id
  parent_id   = aws_api_gateway_resource.product_id.id
  path_part   = "stock"
}

### /cart/{userId} and /cart/{userId}/{productId} ###
resource "aws_api_gateway_resource" "cart_user_id" {
  rest_api_id = aws_api_gateway_rest_api.unified.id
  parent_id   = aws_api_gateway_resource.cart.id
  path_part   = "{userId}"
}

resource "aws_api_gateway_resource" "cart_product_id" {
  rest_api_id = aws_api_gateway_rest_api.unified.id
  parent_id   = aws_api_gateway_resource.cart_user_id.id
  path_part   = "{productId}"
}

### /orders/{orderId} ###
resource "aws_api_gateway_resource" "order_by_id" {
  rest_api_id = aws_api_gateway_rest_api.unified.id
  parent_id   = aws_api_gateway_resource.orders.id
  path_part   = "{orderId}"
}

### /search/reindex ###
resource "aws_api_gateway_resource" "search_reindex" {
  rest_api_id = aws_api_gateway_rest_api.unified.id
  parent_id   = aws_api_gateway_resource.search.id
  path_part   = "reindex"
}

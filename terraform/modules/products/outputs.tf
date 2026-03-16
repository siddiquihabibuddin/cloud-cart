output "product_api_endpoint" {
  description = "[DEV] Product API base URL"
  value       = "https://${aws_api_gateway_rest_api.products.id}.execute-api.${var.aws_region}.amazonaws.com/dev"
}

output "product_api_internal_url" {
  description = "Internal LocalStack URL for product API (used by order service)"
  value       = "http://host.docker.internal:4566/restapis/${aws_api_gateway_rest_api.products.id}/dev/_user_request_"
}

output "products_table_name" {
  description = "Name of the DynamoDB products table"
  value       = aws_dynamodb_table.products.name
}

output "products_table_arn" {
  description = "ARN of the DynamoDB products table"
  value       = aws_dynamodb_table.products.arn
}

output "products_table_stream_arn" {
  description = "Stream ARN of the DynamoDB products table"
  value       = aws_dynamodb_table.products.stream_arn
}

output "list_products_function_arn" {
  value = aws_lambda_function.products["ListProductsFunctionDev"].arn
}

output "create_product_function_arn" {
  value = aws_lambda_function.products["CreateProductFunctionDev"].arn
}

output "get_product_function_arn" {
  value = aws_lambda_function.products["GetProductFunctionDev"].arn
}

output "update_stock_function_arn" {
  value = aws_lambda_function.products["UpdateStockFunctionDev"].arn
}

output "cart_api_endpoint" {
  description = "[DEV] Cart API base URL"
  value       = "https://${aws_api_gateway_rest_api.cart.id}.execute-api.${var.aws_region}.amazonaws.com/dev"
}

output "cart_table_name" {
  description = "DynamoDB table used for cart"
  value       = aws_dynamodb_table.cart.name
}

output "add_to_cart_function_arn" {
  value = aws_lambda_function.cart["AddToCartFunctionDev"].arn
}

output "view_cart_function_arn" {
  value = aws_lambda_function.cart["ViewCartFunctionDev"].arn
}

output "remove_from_cart_function_arn" {
  value = aws_lambda_function.cart["RemoveFromCartFunctionDev"].arn
}

output "update_quantity_function_arn" {
  value = aws_lambda_function.cart["UpdateQuantityFunctionDev"].arn
}

output "clear_cart_function_arn" {
  value = aws_lambda_function.cart["ClearCartFunctionDev"].arn
}

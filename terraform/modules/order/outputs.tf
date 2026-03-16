output "order_api_endpoint" {
  description = "[DEV] Order API base URL"
  value       = "https://${aws_api_gateway_rest_api.order.id}.execute-api.${var.aws_region}.amazonaws.com/dev"
}

output "order_queue_arn" {
  description = "ARN of the order placed SQS queue"
  value       = aws_sqs_queue.order_placed.arn
}

output "order_queue_url" {
  description = "URL of the order placed SQS queue"
  value       = aws_sqs_queue.order_placed.url
}

output "order_placed_dlq_arn" {
  description = "ARN of the OrderPlaced dead letter queue"
  value       = aws_sqs_queue.order_placed_dlq.arn
}

output "payment_success_queue_arn" {
  description = "ARN of the payment success SQS queue"
  value       = aws_sqs_queue.payment_success.arn
}

output "payment_success_queue_url" {
  description = "URL of the payment success SQS queue"
  value       = aws_sqs_queue.payment_success.url
}

output "payment_success_dlq_arn" {
  description = "ARN of the PaymentSuccess dead letter queue"
  value       = aws_sqs_queue.payment_success_dlq.arn
}

output "orders_table_name" {
  description = "DynamoDB table name for orders"
  value       = aws_dynamodb_table.orders.name
}

output "orders_table_arn" {
  description = "ARN of the orders DynamoDB table"
  value       = aws_dynamodb_table.orders.arn
}

output "saga_table_name" {
  description = "DynamoDB table name for saga state"
  value       = aws_dynamodb_table.saga.name
}

output "saga_table_arn" {
  description = "ARN of the saga DynamoDB table"
  value       = aws_dynamodb_table.saga.arn
}

output "stock_compensation_queue_url" {
  description = "URL of the stock compensation SQS queue"
  value       = aws_sqs_queue.stock_compensation.url
}

output "stock_compensation_queue_arn" {
  description = "ARN of the stock compensation SQS queue"
  value       = aws_sqs_queue.stock_compensation.arn
}

output "place_order_function_arn" {
  value = aws_lambda_function.place_order.arn
}

output "get_order_function_arn" {
  value = aws_lambda_function.get_order.arn
}

output "list_orders_function_arn" {
  value = aws_lambda_function.list_orders.arn
}

output "order_api_key_id" {
  description = "ID of the API key for order endpoints"
  value       = aws_api_gateway_api_key.order.id
}

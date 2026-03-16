output "order_shipped_queue_arn" {
  description = "ARN of the OrderShipped SQS queue"
  value       = aws_sqs_queue.order_shipped.arn
}

output "order_shipped_queue_url" {
  description = "URL of the OrderShipped SQS queue"
  value       = aws_sqs_queue.order_shipped.url
}

output "order_shipped_topic_arn" {
  description = "ARN of the OrderShipped SNS topic"
  value       = aws_sns_topic.order_shipped.arn
}

output "shipping_state_machine_arn" {
  description = "ARN of the OrderShipping Step Functions state machine"
  value       = aws_sfn_state_machine.order_shipping.arn
}

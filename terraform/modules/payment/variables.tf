variable "env" {
  type    = string
  default = "Dev"
}

variable "s3_bucket" {
  type = string
}

variable "payment_jar" {
  type    = string
  default = "payment-service-1.0.0.jar"
}

variable "aws_endpoint_url" {
  type = string
}

variable "aws_endpoint_url_internal" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "order_queue_arn" {
  description = "ARN of the OrderPlacedQueue (SQS ESM trigger)"
  type        = string
}

variable "order_placed_dlq_arn" {
  description = "ARN of the OrderPlaced DLQ"
  type        = string
}

variable "payment_success_queue_arn" {
  description = "ARN of the PaymentSuccessQueue"
  type        = string
}

variable "payment_success_queue_url" {
  description = "URL of the PaymentSuccessQueue"
  type        = string
}

variable "orders_table_name" {
  description = "DynamoDB table name for orders"
  type        = string
}

variable "saga_table_name" {
  description = "DynamoDB table name for saga state"
  type        = string
}

variable "saga_table_arn" {
  description = "ARN of the saga DynamoDB table"
  type        = string
}

variable "stock_compensation_queue_url" {
  description = "URL of the StockCompensationQueue"
  type        = string
}

variable "stock_compensation_queue_arn" {
  description = "ARN of the StockCompensationQueue"
  type        = string
}

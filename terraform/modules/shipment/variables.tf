variable "env" {
  type    = string
  default = "Dev"
}

variable "s3_bucket" {
  type = string
}

variable "shipment_jar" {
  type    = string
  default = "shipment-service-1.0.0.jar"
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

variable "payment_success_queue_arn" {
  description = "ARN of the PaymentSuccessQueue (SQS ESM trigger)"
  type        = string
}

variable "payment_success_dlq_arn" {
  description = "ARN of the PaymentSuccess DLQ"
  type        = string
}

variable "orders_table_name" {
  description = "DynamoDB table name for orders"
  type        = string
}

variable "orders_table_arn" {
  description = "ARN of the orders DynamoDB table"
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

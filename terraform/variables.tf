variable "env" {
  description = "Deployment environment suffix (e.g. Dev)"
  type        = string
  default     = "Dev"
}

variable "s3_bucket" {
  description = "S3 bucket that holds the Lambda JAR files"
  type        = string
  default     = "sid-mysourcecode"
}

variable "aws_endpoint_url" {
  description = "LocalStack endpoint URL"
  type        = string
  default     = "http://localhost:4566"
}

variable "aws_endpoint_url_internal" {
  description = "LocalStack endpoint URL as seen from inside Lambda containers (Docker host)"
  type        = string
  default     = "http://host.docker.internal:4566"
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "cart_jar" {
  description = "S3 key for the cart service JAR"
  type        = string
  default     = "cart-service-1.0.0.jar"
}

variable "product_jar" {
  description = "S3 key for the product catalog JAR"
  type        = string
  default     = "product-catalog-1.0.0.jar"
}

variable "order_jar" {
  description = "S3 key for the order service JAR"
  type        = string
  default     = "order-service-1.0.0.jar"
}

variable "payment_jar" {
  description = "S3 key for the payment service JAR"
  type        = string
  default     = "payment-service-1.0.0.jar"
}

variable "shipment_jar" {
  description = "S3 key for the shipment service JAR"
  type        = string
  default     = "shipment-service-1.0.0.jar"
}

variable "search_jar" {
  description = "S3 key for the search service JAR"
  type        = string
  default     = "search-service-1.0.0.jar"
}

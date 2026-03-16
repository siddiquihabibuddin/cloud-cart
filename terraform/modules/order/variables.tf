variable "env" {
  type    = string
  default = "Dev"
}

variable "s3_bucket" {
  type = string
}

variable "order_jar" {
  type    = string
  default = "order-service-1.0.0.jar"
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

variable "products_api_internal_url" {
  description = "Internal LocalStack URL for the product catalog API"
  type        = string
}

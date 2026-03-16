variable "env" {
  type    = string
  default = "Dev"
}

variable "s3_bucket" {
  type = string
}

variable "search_jar" {
  type    = string
  default = "search-service-1.0.0.jar"
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

variable "products_table_name" {
  description = "Name of the DynamoDB products table"
  type        = string
}

variable "products_table_arn" {
  description = "ARN of the DynamoDB products table"
  type        = string
}

variable "products_table_stream_arn" {
  description = "Stream ARN of the DynamoDB products table"
  type        = string
}

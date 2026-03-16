variable "env" {
  type    = string
  default = "Dev"
}

variable "s3_bucket" {
  type = string
}

variable "cart_jar" {
  type    = string
  default = "cart-service-1.0.0.jar"
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

variable "env" {
  type    = string
  default = "Dev"
}

variable "aws_endpoint_url" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "order_api_key_id" {
  description = "ID of the API key created by the order module (reused by unified gateway)"
  type        = string
}

# Products Lambda ARNs
variable "list_products_function_arn" {
  type = string
}

variable "create_product_function_arn" {
  type = string
}

variable "get_product_function_arn" {
  type = string
}

variable "update_stock_function_arn" {
  type = string
}

# Cart Lambda ARNs
variable "add_to_cart_function_arn" {
  type = string
}

variable "view_cart_function_arn" {
  type = string
}

variable "remove_from_cart_function_arn" {
  type = string
}

variable "update_quantity_function_arn" {
  type = string
}

variable "clear_cart_function_arn" {
  type = string
}

# Order Lambda ARNs
variable "place_order_function_arn" {
  type = string
}

variable "get_order_function_arn" {
  type = string
}

variable "list_orders_function_arn" {
  type = string
}

# Search Lambda ARNs
variable "search_products_function_arn" {
  type = string
}

variable "bulk_reindex_function_arn" {
  type = string
}

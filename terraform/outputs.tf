output "cart_api_endpoint" {
  description = "[DEV] Cart API base URL"
  value       = module.cart.cart_api_endpoint
}

output "product_api_endpoint" {
  description = "[DEV] Product API base URL"
  value       = module.products.product_api_endpoint
}

output "product_api_internal_url" {
  description = "Internal LocalStack URL for product API (used by order service)"
  value       = module.products.product_api_internal_url
}

output "order_api_endpoint" {
  description = "[DEV] Order API base URL"
  value       = module.order.order_api_endpoint
}

output "unified_api_endpoint" {
  description = "[DEV] Unified API base URL"
  value       = module.gateway.unified_api_endpoint
}

output "unified_api_internal_url" {
  description = "Internal LocalStack URL for unified API (for frontend next.config.ts)"
  value       = module.gateway.unified_api_internal_url
}

output "opensearch_endpoint" {
  description = "OpenSearch domain endpoint"
  value       = module.search.opensearch_endpoint
}

output "unified_api_endpoint" {
  description = "[DEV] Unified API base URL"
  value       = "https://${aws_api_gateway_rest_api.unified.id}.execute-api.${var.aws_region}.amazonaws.com/dev"
}

output "unified_api_internal_url" {
  description = "Internal LocalStack URL for unified API (for frontend next.config.ts)"
  value       = "http://host.docker.internal:4566/restapis/${aws_api_gateway_rest_api.unified.id}/dev/_user_request_"
}

output "opensearch_endpoint" {
  description = "OpenSearch domain endpoint"
  value       = "http://${aws_opensearch_domain.search.endpoint}"
}

output "search_products_function_arn" {
  value = aws_lambda_function.search_products.arn
}

output "bulk_reindex_function_arn" {
  value = aws_lambda_function.bulk_reindex.arn
}

output "stream_index_function_arn" {
  value = aws_lambda_function.stream_index.arn
}

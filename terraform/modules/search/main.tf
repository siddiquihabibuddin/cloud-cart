### OpenSearch Domain ###
resource "aws_opensearch_domain" "search" {
  domain_name    = "cloudcart-search-dev"
  engine_version = "OpenSearch_2.13"

  cluster_config {
    instance_type  = "t3.small.search"
    instance_count = 1
  }

  ebs_options {
    ebs_enabled = true
    volume_type = "gp2"
    volume_size = 10
  }
}

### IAM Role for Search Lambdas ###
resource "aws_iam_role" "search_lambda" {
  name = "LambdaExecutionRoleSearch"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  depends_on = [aws_opensearch_domain.search]
}

resource "aws_iam_role_policy_attachment" "search_lambda_basic_execution" {
  role       = aws_iam_role.search_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "search_service_access" {
  name = "SearchServiceAccess"
  role = aws_iam_role.search_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["dynamodb:ListStreams"]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetRecords",
          "dynamodb:GetShardIterator",
          "dynamodb:DescribeStream"
        ]
        Resource = var.products_table_stream_arn
      },
      {
        Effect   = "Allow"
        Action   = ["dynamodb:Scan"]
        Resource = var.products_table_arn
      },
      {
        Effect = "Allow"
        Action = [
          "es:ESHttpGet",
          "es:ESHttpPost",
          "es:ESHttpPut",
          "es:ESHttpDelete",
          "es:ESHttpHead"
        ]
        Resource = "arn:aws:es:${var.aws_region}:000000000000:domain/cloudcart-search-dev/*"
      }
    ]
  })
}

### Lambda Functions ###
resource "aws_lambda_function" "stream_index" {
  function_name = "StreamIndexFunctionDev"
  handler       = "com.cloudcart.search.handler.StreamIndexHandler::handleRequest"
  runtime       = "java21"
  role          = aws_iam_role.search_lambda.arn
  s3_bucket     = var.s3_bucket
  s3_key        = var.search_jar
  timeout       = 60
  memory_size   = 256

  environment {
    variables = {
      OPENSEARCH_ENDPOINT = "http://${aws_opensearch_domain.search.endpoint}"
      AWS_ENDPOINT_URL    = var.aws_endpoint_url_internal
    }
  }
}

resource "aws_lambda_function" "search_products" {
  function_name = "SearchProductsFunctionDev"
  handler       = "com.cloudcart.search.handler.SearchProductsHandler::handleRequest"
  runtime       = "java21"
  role          = aws_iam_role.search_lambda.arn
  s3_bucket     = var.s3_bucket
  s3_key        = var.search_jar
  timeout       = 20
  memory_size   = 256

  environment {
    variables = {
      OPENSEARCH_ENDPOINT = "http://${aws_opensearch_domain.search.endpoint}"
      AWS_ENDPOINT_URL    = var.aws_endpoint_url_internal
    }
  }
}

resource "aws_lambda_function" "bulk_reindex" {
  function_name = "BulkReindexFunctionDev"
  handler       = "com.cloudcart.search.handler.BulkReindexHandler::handleRequest"
  runtime       = "java21"
  role          = aws_iam_role.search_lambda.arn
  s3_bucket     = var.s3_bucket
  s3_key        = var.search_jar
  timeout       = 120
  memory_size   = 256

  environment {
    variables = {
      OPENSEARCH_ENDPOINT = "http://${aws_opensearch_domain.search.endpoint}"
      PRODUCTS_TABLE      = var.products_table_name
      AWS_ENDPOINT_URL    = var.aws_endpoint_url_internal
    }
  }
}

### DynamoDB Stream ESM: ProductsTable -> StreamIndexFunction ###
resource "aws_lambda_event_source_mapping" "product_stream" {
  event_source_arn              = var.products_table_stream_arn
  function_name                 = aws_lambda_function.stream_index.arn
  starting_position             = "TRIM_HORIZON"
  batch_size                    = 10
  bisect_batch_on_function_error = true
  function_response_types       = ["ReportBatchItemFailures"]
}

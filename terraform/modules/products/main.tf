### DynamoDB Table ###
# Note: PK is "productID" with uppercase D — must match exactly.
resource "aws_dynamodb_table" "products" {
  name         = "ProductsTableDev"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "productID"

  attribute {
    name = "productID"
    type = "S"
  }

  stream_enabled   = true
  stream_view_type = "NEW_AND_OLD_IMAGES"
}

### IAM Role ###
resource "aws_iam_role" "product_lambda" {
  name = "LambdaExecutionRoleDev"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

}

resource "aws_iam_role_policy_attachment" "product_lambda_basic_execution" {
  role       = aws_iam_role.product_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "product_dynamo_access" {
  name = "DevDynamoDBAccess"
  role = aws_iam_role.product_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:DeleteItem",
        "dynamodb:Scan"
      ]
      Resource = aws_dynamodb_table.products.arn
    }]
  })
}

### Lambda Functions ###
locals {
  product_functions = {
    "ListProductsFunctionDev"   = "com.cloudcart.product.handler.ListProductsHandler::handleRequest"
    "CreateProductFunctionDev"  = "com.cloudcart.product.handler.CreateProductHandler::handleRequest"
    "GetProductFunctionDev"     = "com.cloudcart.product.handler.GetProductHandler::handleRequest"
    "UpdateStockFunctionDev"    = "com.cloudcart.product.handler.UpdateStockHandler::handleRequest"
  }
}

resource "aws_lambda_function" "products" {
  for_each = local.product_functions

  function_name = each.key
  handler       = each.value
  runtime       = "java21"
  role          = aws_iam_role.product_lambda.arn
  s3_bucket     = var.s3_bucket
  s3_key        = var.product_jar
  timeout       = 20
  memory_size   = 128

  environment {
    variables = {
      PRODUCTS_TABLE   = aws_dynamodb_table.products.name
      ENV              = "dev"
      AWS_ENDPOINT_URL = var.aws_endpoint_url_internal
    }
  }
}

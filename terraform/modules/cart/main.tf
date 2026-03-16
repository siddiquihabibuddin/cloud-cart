### DynamoDB Table for Cart ###
resource "aws_dynamodb_table" "cart" {
  name         = "CartTableDev"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "userId"
  range_key    = "productId"

  attribute {
    name = "userId"
    type = "S"
  }

  attribute {
    name = "productId"
    type = "S"
  }
}

### IAM Role for Cart Lambda ###
resource "aws_iam_role" "cart_lambda" {
  name = "CartLambdaExecutionRoleDev"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

}

resource "aws_iam_role_policy_attachment" "cart_lambda_basic_execution" {
  role       = aws_iam_role.cart_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "cart_dynamo_access" {
  name = "DevCartDynamoDBAccess"
  role = aws_iam_role.cart_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:DeleteItem",
        "dynamodb:Scan",
        "dynamodb:Query",
        "dynamodb:BatchWriteItem"
      ]
      Resource = aws_dynamodb_table.cart.arn
    }]
  })
}

### Lambda Functions ###
locals {
  cart_functions = {
    "AddToCartFunctionDev"    = "com.cloudcart.cart.handler.AddToCartHandler::handleRequest"
    "ViewCartFunctionDev"     = "com.cloudcart.cart.handler.ViewCartHandler::handleRequest"
    "RemoveFromCartFunctionDev" = "com.cloudcart.cart.handler.RemoveFromCartHandler::handleRequest"
    "UpdateQuantityFunctionDev" = "com.cloudcart.cart.handler.UpdateQuantityHandler::handleRequest"
    "ClearCartFunctionDev"    = "com.cloudcart.cart.handler.ClearCartHandler::handleRequest"
  }
}

resource "aws_lambda_function" "cart" {
  for_each = local.cart_functions

  function_name = each.key
  handler       = each.value
  runtime       = "java21"
  role          = aws_iam_role.cart_lambda.arn
  s3_bucket     = var.s3_bucket
  s3_key        = var.cart_jar
  timeout       = 10
  memory_size   = 128

  environment {
    variables = {
      CART_TABLE       = aws_dynamodb_table.cart.name
      ENV              = "dev"
      AWS_ENDPOINT_URL = var.aws_endpoint_url_internal
    }
  }
}

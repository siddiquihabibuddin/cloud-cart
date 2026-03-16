### IAM Role for Payment Lambda ###
resource "aws_iam_role" "payment_lambda" {
  name = "PaymentLambdaRoleDev"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

}

resource "aws_iam_role_policy_attachment" "payment_lambda_basic_execution" {
  role       = aws_iam_role.payment_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "payment_sqs_dynamo_access" {
  name = "PaymentSQSDynamoAccess"
  role = aws_iam_role.payment_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes"
        ]
        Resource = var.order_queue_arn
      },
      {
        Effect = "Allow"
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes"
        ]
        Resource = var.order_placed_dlq_arn
      },
      {
        Effect   = "Allow"
        Action   = ["sqs:SendMessage"]
        Resource = var.payment_success_queue_arn
      },
      {
        Effect   = "Allow"
        Action   = ["dynamodb:UpdateItem"]
        Resource = "arn:aws:dynamodb:${var.aws_region}:000000000000:table/OrdersTableDev"
      },
      {
        Effect   = "Allow"
        Action   = ["dynamodb:UpdateItem"]
        Resource = var.saga_table_arn
      },
      {
        Effect   = "Allow"
        Action   = ["sqs:SendMessage"]
        Resource = var.stock_compensation_queue_arn
      }
    ]
  })
}

### Payment Lambda Function ###
resource "aws_lambda_function" "process_payment" {
  function_name = "ProcessPaymentFunctionDev"
  handler       = "com.cloudcart.payment.handler.ProcessPaymentHandler::handleRequest"
  runtime       = "java21"
  role          = aws_iam_role.payment_lambda.arn
  s3_bucket     = var.s3_bucket
  s3_key        = var.payment_jar
  timeout       = 30
  memory_size   = 256

  environment {
    variables = {
      ORDERS_TABLE                 = var.orders_table_name
      PAYMENT_SUCCESS_QUEUE_URL    = var.payment_success_queue_url
      SAGA_TABLE                   = var.saga_table_name
      STOCK_COMPENSATION_QUEUE_URL = var.stock_compensation_queue_url
      AWS_ENDPOINT_URL             = var.aws_endpoint_url_internal
    }
  }
}

### SQS Event Source Mapping ###
resource "aws_lambda_event_source_mapping" "order_queue" {
  event_source_arn        = var.order_queue_arn
  function_name           = aws_lambda_function.process_payment.arn
  batch_size              = 5
  enabled                 = true
  function_response_types = ["ReportBatchItemFailures"]
}

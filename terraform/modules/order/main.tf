### DynamoDB Table for Saga ###
resource "aws_dynamodb_table" "saga" {
  name         = "SagaTableDev"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "orderId"

  attribute {
    name = "orderId"
    type = "S"
  }

  ttl {
    attribute_name = "expiresAt"
    enabled        = true
  }
}

### DynamoDB Table for Orders ###
resource "aws_dynamodb_table" "orders" {
  name         = "OrdersTableDev"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "orderId"

  attribute {
    name = "orderId"
    type = "S"
  }

  attribute {
    name = "userId"
    type = "S"
  }

  global_secondary_index {
    name            = "userId-index"
    hash_key        = "userId"
    projection_type = "ALL"
  }
}

### DynamoDB Table for Idempotency ###
resource "aws_dynamodb_table" "idempotency" {
  name         = "IdempotencyTableDev"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "idempotencyKey"

  attribute {
    name = "idempotencyKey"
    type = "S"
  }

  ttl {
    attribute_name = "expiresAt"
    enabled        = true
  }
}

### Dead Letter Queues ###
resource "aws_sqs_queue" "stock_compensation_dlq" {
  name = "StockCompensationDLQDev"
}

resource "aws_sqs_queue" "order_placed_dlq" {
  name = "OrderPlacedDLQDev"
}

resource "aws_sqs_queue" "payment_success_dlq" {
  name = "PaymentSuccessDLQDev"
}

### SQS Queues with Redrive ###
resource "aws_sqs_queue" "stock_compensation" {
  name                       = "StockCompensationQueueDev"
  visibility_timeout_seconds = 60

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.stock_compensation_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue" "order_placed" {
  name                       = "OrderPlacedQueueDev"
  visibility_timeout_seconds = 60

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.order_placed_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue" "payment_success" {
  name                       = "PaymentSuccessQueueDev"
  visibility_timeout_seconds = 60

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.payment_success_dlq.arn
    maxReceiveCount     = 3
  })
}

### CloudWatch Alarms for DLQs ###
resource "aws_cloudwatch_metric_alarm" "order_placed_dlq" {
  alarm_name          = "cloudcart-OrderPlacedDLQ-MessagesVisible"
  alarm_description   = "Alert when messages appear in OrderPlacedDLQ"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    QueueName = aws_sqs_queue.order_placed_dlq.name
  }
}

resource "aws_cloudwatch_metric_alarm" "payment_success_dlq" {
  alarm_name          = "cloudcart-PaymentSuccessDLQ-MessagesVisible"
  alarm_description   = "Alert when messages appear in PaymentSuccessDLQ"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    QueueName = aws_sqs_queue.payment_success_dlq.name
  }
}

resource "aws_cloudwatch_metric_alarm" "stock_compensation_dlq" {
  alarm_name          = "cloudcart-StockCompensationDLQ-MessagesVisible"
  alarm_description   = "Alert when messages appear in StockCompensationDLQ"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    QueueName = aws_sqs_queue.stock_compensation_dlq.name
  }
}

### IAM Role for Order Lambdas ###
resource "aws_iam_role" "order_lambda" {
  name = "OrderLambdaRoleDev"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

}

resource "aws_iam_role_policy_attachment" "order_lambda_basic_execution" {
  role       = aws_iam_role.order_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "order_dynamo_sqs_access" {
  name = "OrderDynamoSQSAccess"
  role = aws_iam_role.order_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:DeleteItem",
          "dynamodb:Scan",
          "dynamodb:Query"
        ]
        Resource = [
          aws_dynamodb_table.orders.arn,
          "${aws_dynamodb_table.orders.arn}/index/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem"
        ]
        Resource = aws_dynamodb_table.idempotency.arn
      },
      {
        Effect   = "Allow"
        Action   = ["sqs:SendMessage"]
        Resource = aws_sqs_queue.order_placed.arn
      },
      {
        Effect = "Allow"
        Action = [
          "dynamodb:PutItem",
          "dynamodb:UpdateItem"
        ]
        Resource = aws_dynamodb_table.saga.arn
      }
    ]
  })
}

### IAM Role for Compensation Lambda ###
resource "aws_iam_role" "compensation_lambda" {
  name = "CompensationLambdaRoleDev"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

}

resource "aws_iam_role_policy_attachment" "compensation_lambda_basic_execution" {
  role       = aws_iam_role.compensation_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "compensation_sqs_dynamo_access" {
  name = "CompensationSQSDynamoAccess"
  role = aws_iam_role.compensation_lambda.id

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
        Resource = [
          aws_sqs_queue.stock_compensation.arn,
          aws_sqs_queue.stock_compensation_dlq.arn
        ]
      },
      {
        Effect   = "Allow"
        Action   = ["dynamodb:UpdateItem"]
        Resource = aws_dynamodb_table.saga.arn
      }
    ]
  })
}

### Lambda Functions ###
resource "aws_lambda_function" "place_order" {
  function_name = "PlaceOrderFunctionDev"
  handler       = "com.cloudcart.order.handler.PlaceOrderHandler::handleRequest"
  runtime       = "java21"
  role          = aws_iam_role.order_lambda.arn
  s3_bucket     = var.s3_bucket
  s3_key        = var.order_jar
  timeout       = 15
  memory_size   = 256

  environment {
    variables = {
      ORDERS_TABLE       = aws_dynamodb_table.orders.name
      ORDER_QUEUE_URL    = aws_sqs_queue.order_placed.url
      PRODUCTS_API_URL   = var.products_api_internal_url
      IDEMPOTENCY_TABLE  = aws_dynamodb_table.idempotency.name
      SAGA_TABLE         = aws_dynamodb_table.saga.name
      AWS_ENDPOINT_URL   = var.aws_endpoint_url_internal
    }
  }
}

resource "aws_lambda_function" "get_order" {
  function_name = "GetOrderFunctionDev"
  handler       = "com.cloudcart.order.handler.GetOrderHandler::handleRequest"
  runtime       = "java21"
  role          = aws_iam_role.order_lambda.arn
  s3_bucket     = var.s3_bucket
  s3_key        = var.order_jar
  timeout       = 15
  memory_size   = 256

  environment {
    variables = {
      ORDERS_TABLE     = aws_dynamodb_table.orders.name
      ORDER_QUEUE_URL  = aws_sqs_queue.order_placed.url
      AWS_ENDPOINT_URL = var.aws_endpoint_url_internal
    }
  }
}

resource "aws_lambda_function" "list_orders" {
  function_name = "ListOrdersFunctionDev"
  handler       = "com.cloudcart.order.handler.ListOrdersHandler::handleRequest"
  runtime       = "java21"
  role          = aws_iam_role.order_lambda.arn
  s3_bucket     = var.s3_bucket
  s3_key        = var.order_jar
  timeout       = 15
  memory_size   = 256

  environment {
    variables = {
      ORDERS_TABLE     = aws_dynamodb_table.orders.name
      ORDER_QUEUE_URL  = aws_sqs_queue.order_placed.url
      AWS_ENDPOINT_URL = var.aws_endpoint_url_internal
    }
  }
}

resource "aws_lambda_function" "process_compensation" {
  function_name = "ProcessCompensationFunctionDev"
  handler       = "com.cloudcart.order.handler.ProcessCompensationHandler::handleRequest"
  runtime       = "java21"
  role          = aws_iam_role.compensation_lambda.arn
  s3_bucket     = var.s3_bucket
  s3_key        = var.order_jar
  timeout       = 60
  memory_size   = 256

  environment {
    variables = {
      SAGA_TABLE        = aws_dynamodb_table.saga.name
      PRODUCTS_API_URL  = var.products_api_internal_url
      AWS_ENDPOINT_URL  = var.aws_endpoint_url_internal
    }
  }
}

### SQS ESM for Compensation ###
resource "aws_lambda_event_source_mapping" "compensation_queue" {
  event_source_arn        = aws_sqs_queue.stock_compensation.arn
  function_name           = aws_lambda_function.process_compensation.arn
  batch_size              = 1
  enabled                 = true
  function_response_types = ["ReportBatchItemFailures"]
}

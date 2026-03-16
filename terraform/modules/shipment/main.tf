### IAM Role for Shipment Lambda ###
resource "aws_iam_role" "shipment_lambda" {
  name = "ShipmentLambdaRoleDev"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

}

resource "aws_iam_role_policy_attachment" "shipment_lambda_basic_execution" {
  role       = aws_iam_role.shipment_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "shipment_sqs_dynamo_access" {
  name = "ShipmentSQSDynamoAccess"
  role = aws_iam_role.shipment_lambda.id

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
          var.payment_success_queue_arn,
          var.payment_success_dlq_arn
        ]
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
      }
    ]
  })
}

### Shipment Lambda Function (PaymentSuccessQueue -> SHIPPED) ###
resource "aws_lambda_function" "process_shipment" {
  function_name = "ProcessShipmentFunctionDev"
  handler       = "com.cloudcart.shipment.handler.ProcessShipmentHandler::handleRequest"
  runtime       = "java21"
  role          = aws_iam_role.shipment_lambda.arn
  s3_bucket     = var.s3_bucket
  s3_key        = var.shipment_jar
  timeout       = 30
  memory_size   = 256

  environment {
    variables = {
      ORDERS_TABLE     = var.orders_table_name
      SAGA_TABLE       = var.saga_table_name
      AWS_ENDPOINT_URL = var.aws_endpoint_url_internal
    }
  }
}

### SQS ESM: PaymentSuccessQueue -> ProcessShipmentFunctionDev ###
resource "aws_lambda_event_source_mapping" "payment_success_queue" {
  event_source_arn        = var.payment_success_queue_arn
  function_name           = aws_lambda_function.process_shipment.arn
  batch_size              = 5
  enabled                 = true
  function_response_types = ["ReportBatchItemFailures"]
}

### SNS Topic for shipping notifications ###
resource "aws_sns_topic" "order_shipped" {
  name = "OrderShippedTopicDev"
}

### OrderShipped DLQ and Queue ###
resource "aws_sqs_queue" "order_shipped_dlq" {
  name = "OrderShippedDLQDev"
}

resource "aws_sqs_queue" "order_shipped" {
  name                       = "OrderShippedQueueDev"
  visibility_timeout_seconds = 60

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.order_shipped_dlq.arn
    maxReceiveCount     = 3
  })
}

### CloudWatch Alarm for OrderShipped DLQ ###
resource "aws_cloudwatch_metric_alarm" "order_shipped_dlq" {
  alarm_name          = "cloudcart-OrderShippedDLQ-MessagesVisible"
  alarm_description   = "Alert when messages appear in OrderShippedDLQ"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    QueueName = aws_sqs_queue.order_shipped_dlq.name
  }
}

### IAM Role for ScanShipmentCreated Lambda ###
resource "aws_iam_role" "scan_shipment_lambda" {
  name = "ScanShipmentLambdaRoleDev"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

}

resource "aws_iam_role_policy_attachment" "scan_shipment_lambda_basic_execution" {
  role       = aws_iam_role.scan_shipment_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "scan_shipment_access" {
  name = "ScanShipmentAccess"
  role = aws_iam_role.scan_shipment_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["dynamodb:Scan"]
        Resource = "arn:aws:dynamodb:${var.aws_region}:000000000000:table/OrdersTableDev"
      },
      {
        Effect   = "Allow"
        Action   = ["sqs:SendMessage"]
        Resource = aws_sqs_queue.order_shipped.arn
      }
    ]
  })
}

### IAM Role for ProcessOrderShipped Lambda ###
resource "aws_iam_role" "order_shipped_lambda" {
  name = "OrderShippedLambdaRoleDev"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

}

resource "aws_iam_role_policy_attachment" "order_shipped_lambda_basic_execution" {
  role       = aws_iam_role.order_shipped_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "order_shipped_access" {
  name = "OrderShippedAccess"
  role = aws_iam_role.order_shipped_lambda.id

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
          aws_sqs_queue.order_shipped.arn,
          aws_sqs_queue.order_shipped_dlq.arn
        ]
      },
      {
        Effect   = "Allow"
        Action   = ["dynamodb:UpdateItem"]
        Resource = "arn:aws:dynamodb:${var.aws_region}:000000000000:table/OrdersTableDev"
      },
      {
        Effect   = "Allow"
        Action   = ["sns:Publish"]
        Resource = aws_sns_topic.order_shipped.arn
      }
    ]
  })
}

### ScanShipmentCreated Lambda ###
resource "aws_lambda_function" "scan_shipment_created" {
  function_name = "ScanShipmentCreatedFunctionDev"
  handler       = "com.cloudcart.shipment.handler.ScanShipmentCreatedHandler::handleRequest"
  runtime       = "java21"
  role          = aws_iam_role.scan_shipment_lambda.arn
  s3_bucket     = var.s3_bucket
  s3_key        = var.shipment_jar
  timeout       = 60
  memory_size   = 256

  environment {
    variables = {
      ORDERS_TABLE             = var.orders_table_name
      ORDER_SHIPPED_QUEUE_URL  = aws_sqs_queue.order_shipped.url
      AWS_ENDPOINT_URL         = var.aws_endpoint_url_internal
    }
  }
}

### ProcessOrderShipped Lambda ###
resource "aws_lambda_function" "process_order_shipped" {
  function_name = "ProcessOrderShippedFunctionDev"
  handler       = "com.cloudcart.shipment.handler.ProcessOrderShippedHandler::handleRequest"
  runtime       = "java21"
  role          = aws_iam_role.order_shipped_lambda.arn
  s3_bucket     = var.s3_bucket
  s3_key        = var.shipment_jar
  timeout       = 30
  memory_size   = 256

  environment {
    variables = {
      ORDERS_TABLE              = var.orders_table_name
      ORDER_SHIPPED_TOPIC_ARN   = aws_sns_topic.order_shipped.arn
      AWS_ENDPOINT_URL          = var.aws_endpoint_url_internal
    }
  }
}

### ESM: OrderShippedQueue -> ProcessOrderShippedFunctionDev ###
resource "aws_lambda_event_source_mapping" "order_shipped_queue" {
  event_source_arn        = aws_sqs_queue.order_shipped.arn
  function_name           = aws_lambda_function.process_order_shipped.arn
  batch_size              = 5
  enabled                 = true
  function_response_types = ["ReportBatchItemFailures"]
}

### IAM Role for Step Functions State Machine ###
resource "aws_iam_role" "shipping_state_machine" {
  name = "ShippingStateMachineRoleDev"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "states.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "invoke_scan_lambda" {
  name = "InvokeScanLambda"
  role = aws_iam_role.shipping_state_machine.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "lambda:InvokeFunction"
      Resource = aws_lambda_function.scan_shipment_created.arn
    }]
  })
}

### IAM Role for EventBridge Scheduler ###
resource "aws_iam_role" "shipping_scheduler" {
  name = "ShippingSchedulerRoleDev"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "events.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "start_state_machine" {
  name = "StartStateMachine"
  role = aws_iam_role.shipping_scheduler.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "states:StartExecution"
      Resource = aws_sfn_state_machine.order_shipping.arn
    }]
  })
}

### Step Functions State Machine ###
resource "aws_sfn_state_machine" "order_shipping" {
  name     = "OrderShippingStateMachineDev"
  role_arn = aws_iam_role.shipping_state_machine.arn

  definition = jsonencode({
    Comment  = "Scan SHIPMENT_CREATED orders and publish OrderShippedEvents"
    StartAt  = "ScanAndPublish"
    States = {
      ScanAndPublish = {
        Type     = "Task"
        Resource = aws_lambda_function.scan_shipment_created.arn
        End      = true
      }
    }
  })
}

### EventBridge Rule: trigger every 5 minutes ###
resource "aws_cloudwatch_event_rule" "shipping_schedule" {
  name                = "cloudcart-shipping-schedule-dev"
  schedule_expression = "rate(5 minutes)"
  state               = "ENABLED"
}

resource "aws_cloudwatch_event_target" "shipping_state_machine" {
  rule     = aws_cloudwatch_event_rule.shipping_schedule.name
  arn      = aws_sfn_state_machine.order_shipping.arn
  role_arn = aws_iam_role.shipping_scheduler.arn
}

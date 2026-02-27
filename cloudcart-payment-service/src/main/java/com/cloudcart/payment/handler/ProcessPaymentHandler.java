package com.cloudcart.payment.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.payment.model.OrderPlacedEvent;
import com.cloudcart.payment.model.PaymentSuccessEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class ProcessPaymentHandler implements RequestHandler<Map<String, Object>, Void> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DynamoDbClient dynamoDbClient;
    private final SqsClient sqsClient;
    private final String ordersTable = System.getenv("ORDERS_TABLE");
    private final String paymentSuccessQueueUrl = System.getenv("PAYMENT_SUCCESS_QUEUE_URL");

    public ProcessPaymentHandler() {
        String endpointUrl = System.getenv("AWS_ENDPOINT_URL");

        DynamoDbClientBuilder dynamoBuilder = DynamoDbClient.builder();
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            dynamoBuilder.endpointOverride(URI.create(endpointUrl));
        }
        this.dynamoDbClient = dynamoBuilder.build();

        SqsClientBuilder sqsBuilder = SqsClient.builder();
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            sqsBuilder.endpointOverride(URI.create(endpointUrl));
        }
        this.sqsClient = sqsBuilder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Void handleRequest(Map<String, Object> input, Context context) {
        List<Map<String, Object>> records = (List<Map<String, Object>>) input.get("Records");
        if (records == null) {
            context.getLogger().log("No records in SQS event");
            return null;
        }

        for (Map<String, Object> record : records) {
            try {
                String body = (String) record.get("body");
                OrderPlacedEvent event = mapper.readValue(body, OrderPlacedEvent.class);

                String status = Math.random() < 0.8 ? "PAID" : "FAILED";

                dynamoDbClient.updateItem(UpdateItemRequest.builder()
                        .tableName(ordersTable)
                        .key(Map.of("orderId", AttributeValue.fromS(event.getOrderId())))
                        .updateExpression("SET #s = :status")
                        .expressionAttributeNames(Map.of("#s", "status"))
                        .expressionAttributeValues(Map.of(":status", AttributeValue.fromS(status)))
                        .build());

                context.getLogger().log("Payment processed for order " + event.getOrderId()
                        + " (user: " + event.getUserId()
                        + ", total: " + event.getTotalAmount()
                        + "): " + status);

                if ("PAID".equals(status) && paymentSuccessQueueUrl != null) {
                    PaymentSuccessEvent successEvent = new PaymentSuccessEvent(
                            event.getOrderId(), event.getUserId(), event.getItems(), event.getTotalAmount()
                    );
                    String eventJson = mapper.writeValueAsString(successEvent);
                    sqsClient.sendMessage(SendMessageRequest.builder()
                            .queueUrl(paymentSuccessQueueUrl)
                            .messageBody(eventJson)
                            .build());
                    context.getLogger().log("Published PaymentSuccessEvent for order " + event.getOrderId());
                }

            } catch (Exception e) {
                context.getLogger().log("Error processing payment record: " + e.getMessage());
            }
        }

        return null;
    }
}

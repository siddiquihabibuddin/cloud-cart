package com.cloudcart.payment.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.payment.model.OrderPlacedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class ProcessPaymentHandler implements RequestHandler<Map<String, Object>, Void> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DynamoDbClient dynamoDbClient;
    private final String ordersTable = System.getenv("ORDERS_TABLE");

    public ProcessPaymentHandler() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder();
        String endpointUrl = System.getenv("AWS_ENDPOINT_URL");
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        this.dynamoDbClient = builder.build();
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

            } catch (Exception e) {
                context.getLogger().log("Error processing payment record: " + e.getMessage());
            }
        }

        return null;
    }
}

package com.cloudcart.shipment.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.shipment.model.PaymentSuccessEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProcessShipmentHandler implements RequestHandler<Map<String, Object>, Void> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DynamoDbClient dynamoDbClient;
    private final String ordersTable = System.getenv("ORDERS_TABLE");

    public ProcessShipmentHandler() {
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
                PaymentSuccessEvent event = mapper.readValue(body, PaymentSuccessEvent.class);

                String trackingId = "TRK-" + UUID.randomUUID().toString()
                        .replace("-", "").substring(0, 8).toUpperCase();
                String shippedAt = Instant.now().toString();

                dynamoDbClient.updateItem(UpdateItemRequest.builder()
                        .tableName(ordersTable)
                        .key(Map.of("orderId", AttributeValue.fromS(event.getOrderId())))
                        .updateExpression("SET #s = :status, trackingId = :tid, shippedAt = :ts")
                        .expressionAttributeNames(Map.of("#s", "status"))
                        .expressionAttributeValues(Map.of(
                                ":status", AttributeValue.fromS("SHIPPED"),
                                ":tid", AttributeValue.fromS(trackingId),
                                ":ts", AttributeValue.fromS(shippedAt)
                        ))
                        .build());

                context.getLogger().log("Shipment initiated for order " + event.getOrderId()
                        + ", tracking: " + trackingId);

            } catch (Exception e) {
                context.getLogger().log("Error processing shipment record: " + e.getMessage());
            }
        }

        return null;
    }
}

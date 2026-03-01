package com.cloudcart.order.repository;

import com.cloudcart.order.model.OrderItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SagaRepository {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DynamoDbClient dynamoClient;
    private final String tableName;

    public SagaRepository(DynamoDbClient dynamoClient, String tableName) {
        this.dynamoClient = dynamoClient;
        this.tableName = tableName;
    }

    public void createSaga(String orderId, String userId, List<OrderItem> reservedItems) {
        try {
            String itemsJson = MAPPER.writeValueAsString(reservedItems);
            long expiresAt = Instant.now().getEpochSecond() + (7 * 24 * 60 * 60); // 7 days TTL

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("orderId", AttributeValue.fromS(orderId));
            item.put("sagaStatus", AttributeValue.fromS("STARTED"));
            item.put("currentStep", AttributeValue.fromS("STOCK_RESERVED"));
            item.put("reservedItems", AttributeValue.fromS(itemsJson));
            item.put("userId", AttributeValue.fromS(userId));
            item.put("createdAt", AttributeValue.fromS(Instant.now().toString()));
            item.put("updatedAt", AttributeValue.fromS(Instant.now().toString()));
            item.put("expiresAt", AttributeValue.fromN(String.valueOf(expiresAt)));

            dynamoClient.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .conditionExpression("attribute_not_exists(orderId)")
                    .build());
        } catch (ConditionalCheckFailedException e) {
            // Already exists — idempotent, skip silently
        } catch (Exception e) {
            throw new RuntimeException("Failed to create saga record", e);
        }
    }

    public void updateSagaStatus(String orderId, String expectedStatus, String newSagaStatus, String newStep) {
        try {
            Map<String, AttributeValue> expressionValues = new HashMap<>();
            expressionValues.put(":s", AttributeValue.fromS(newSagaStatus));
            expressionValues.put(":now", AttributeValue.fromS(Instant.now().toString()));
            expressionValues.put(":expectedStatus", AttributeValue.fromS(expectedStatus));

            String updateExpression;
            if (newStep != null) {
                expressionValues.put(":step", AttributeValue.fromS(newStep));
                updateExpression = "SET sagaStatus = :s, currentStep = :step, updatedAt = :now";
            } else {
                updateExpression = "SET sagaStatus = :s, updatedAt = :now";
            }

            dynamoClient.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("orderId", AttributeValue.fromS(orderId)))
                    .updateExpression(updateExpression)
                    .conditionExpression("sagaStatus = :expectedStatus")
                    .expressionAttributeValues(expressionValues)
                    .build());
        } catch (ConditionalCheckFailedException e) {
            // Already advanced to a later state — idempotent, skip silently
        }
    }
}

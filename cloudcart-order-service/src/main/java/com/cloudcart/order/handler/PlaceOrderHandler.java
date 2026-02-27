package com.cloudcart.order.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.order.model.Order;
import com.cloudcart.order.model.OrderItem;
import com.cloudcart.order.model.OrderPlacedEvent;
import com.cloudcart.order.repository.OrderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlaceOrderHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OrderRepository repository = new OrderRepository();
    private final SqsClient sqsClient;
    private final DynamoDbClient productsClient;
    private final String queueUrl = System.getenv("ORDER_QUEUE_URL");
    private final String productsTable = System.getenv("PRODUCTS_TABLE");

    public PlaceOrderHandler() {
        String endpointUrl = System.getenv("AWS_ENDPOINT_URL");

        SqsClientBuilder sqsBuilder = SqsClient.builder();
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            sqsBuilder.endpointOverride(URI.create(endpointUrl));
        }
        this.sqsClient = sqsBuilder.build();

        DynamoDbClientBuilder dynamoBuilder = DynamoDbClient.builder();
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            dynamoBuilder.endpointOverride(URI.create(endpointUrl));
        }
        this.productsClient = dynamoBuilder.build();
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            String body = (String) input.get("body");
            Map<String, Object> requestBody = mapper.readValue(body, new TypeReference<>() {});

            String userId = (String) requestBody.get("userId");
            List<OrderItem> items = mapper.convertValue(
                    requestBody.get("items"),
                    new TypeReference<List<OrderItem>>() {}
            );

            if (userId == null || items == null || items.isEmpty()) {
                return response(400, "{\"error\":\"userId and items are required\"}");
            }

            // Phase 1: stock check (non-locking read, fast-fail)
            List<Map<String, String>> outOfStock = new ArrayList<>();
            for (OrderItem item : items) {
                Map<String, AttributeValue> key = Map.of(
                        "productID", AttributeValue.fromS(item.getProductId())
                );
                GetItemResponse productResponse = productsClient.getItem(GetItemRequest.builder()
                        .tableName(productsTable)
                        .key(key)
                        .build());

                if (!productResponse.hasItem() || productResponse.item().isEmpty()) {
                    outOfStock.add(Map.of("productId", item.getProductId(), "reason", "not found"));
                } else {
                    AttributeValue stockAttr = productResponse.item().get("stock");
                    int stock = stockAttr != null ? Integer.parseInt(stockAttr.n()) : 0;
                    if (stock < item.getQuantity()) {
                        outOfStock.add(Map.of("productId", item.getProductId(),
                                "available", String.valueOf(stock),
                                "requested", String.valueOf(item.getQuantity())));
                    }
                }
            }

            if (!outOfStock.isEmpty()) {
                String errorBody = mapper.writeValueAsString(Map.of(
                        "error", "Insufficient stock",
                        "items", outOfStock
                ));
                return response(409, errorBody);
            }

            // Phase 2: conditional reservation (atomic decrement per item)
            List<OrderItem> reserved = new ArrayList<>();
            for (OrderItem item : items) {
                Map<String, AttributeValue> key = Map.of(
                        "productID", AttributeValue.fromS(item.getProductId())
                );
                Map<String, AttributeValue> exprValues = new HashMap<>();
                exprValues.put(":qty", AttributeValue.fromN(String.valueOf(item.getQuantity())));

                try {
                    productsClient.updateItem(UpdateItemRequest.builder()
                            .tableName(productsTable)
                            .key(key)
                            .updateExpression("SET stock = stock - :qty")
                            .conditionExpression("stock >= :qty")
                            .expressionAttributeValues(exprValues)
                            .build());
                    reserved.add(item);
                } catch (ConditionalCheckFailedException e) {
                    // Rollback already-reserved items
                    for (OrderItem reservedItem : reserved) {
                        Map<String, AttributeValue> rollbackKey = Map.of(
                                "productID", AttributeValue.fromS(reservedItem.getProductId())
                        );
                        Map<String, AttributeValue> rollbackValues = Map.of(
                                ":qty", AttributeValue.fromN(String.valueOf(reservedItem.getQuantity()))
                        );
                        productsClient.updateItem(UpdateItemRequest.builder()
                                .tableName(productsTable)
                                .key(rollbackKey)
                                .updateExpression("SET stock = stock + :qty")
                                .expressionAttributeValues(rollbackValues)
                                .build());
                    }
                    String errorBody = mapper.writeValueAsString(Map.of(
                            "error", "Insufficient stock",
                            "items", List.of(Map.of("productId", item.getProductId(), "reason", "race condition"))
                    ));
                    return response(409, errorBody);
                }
            }

            double total = items.stream()
                    .mapToDouble(i -> i.getPrice() * i.getQuantity())
                    .sum();

            String orderId = UUID.randomUUID().toString();
            String itemsJson = mapper.writeValueAsString(items);

            Order order = new Order();
            order.setOrderId(orderId);
            order.setUserId(userId);
            order.setItemsJson(itemsJson);
            order.setTotalAmount(total);
            order.setStatus("PENDING");
            order.setCreatedAt(Instant.now().toString());

            repository.saveOrder(order);

            OrderPlacedEvent event = new OrderPlacedEvent(orderId, userId, items, total);
            String eventJson = mapper.writeValueAsString(event);

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(eventJson)
                    .build());

            context.getLogger().log("Order placed: " + orderId + " for user: " + userId);

            String responseBody = mapper.writeValueAsString(Map.of("orderId", orderId));
            return Map.of(
                    "statusCode", 201,
                    "headers", Map.of("Content-Type", "application/json"),
                    "body", responseBody
            );
        } catch (Exception e) {
            context.getLogger().log("Error placing order: " + e.getMessage());
            return response(500, "{\"error\":\"Failed to place order\"}");
        }
    }

    private Map<String, Object> response(int statusCode, String body) {
        return Map.of(
                "statusCode", statusCode,
                "headers", Map.of("Content-Type", "application/json"),
                "body", body
        );
    }
}

package com.cloudcart.order.repository;

import com.cloudcart.order.model.Order;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName = System.getenv("ORDERS_TABLE");

    public OrderRepository() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder();
        String endpointUrl = System.getenv("AWS_ENDPOINT_URL");
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        this.dynamoDbClient = builder.build();
    }

    public void saveOrder(Order order) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("orderId", AttributeValue.fromS(order.getOrderId()));
        item.put("userId", AttributeValue.fromS(order.getUserId()));
        item.put("itemsJson", AttributeValue.fromS(order.getItemsJson()));
        item.put("totalAmount", AttributeValue.fromN(String.valueOf(order.getTotalAmount())));
        item.put("status", AttributeValue.fromS(order.getStatus()));
        item.put("createdAt", AttributeValue.fromS(order.getCreatedAt()));

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());
    }

    public Order getOrder(String orderId) {
        Map<String, AttributeValue> key = Map.of("orderId", AttributeValue.fromS(orderId));

        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build());

        if (!response.hasItem() || response.item().isEmpty()) {
            return null;
        }

        return toOrder(response.item());
    }

    public List<Order> listByUser(String userId) {
        Map<String, AttributeValue> exprValues = Map.of(":uid", AttributeValue.fromS(userId));

        ScanResponse response = dynamoDbClient.scan(ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("userId = :uid")
                .expressionAttributeValues(exprValues)
                .build());

        List<Order> orders = new ArrayList<>();
        for (Map<String, AttributeValue> row : response.items()) {
            orders.add(toOrder(row));
        }
        return orders;
    }

    public void updateStatus(String orderId, String status) {
        Map<String, AttributeValue> key = Map.of("orderId", AttributeValue.fromS(orderId));

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression("SET #s = :status")
                .expressionAttributeNames(Map.of("#s", "status"))
                .expressionAttributeValues(Map.of(":status", AttributeValue.fromS(status)))
                .build());
    }

    private Order toOrder(Map<String, AttributeValue> row) {
        Order order = new Order();
        order.setOrderId(row.get("orderId").s());
        order.setUserId(row.get("userId").s());
        order.setItemsJson(row.get("itemsJson").s());
        order.setTotalAmount(Double.parseDouble(row.get("totalAmount").n()));
        order.setStatus(row.get("status").s());
        order.setCreatedAt(row.get("createdAt").s());
        return order;
    }
}

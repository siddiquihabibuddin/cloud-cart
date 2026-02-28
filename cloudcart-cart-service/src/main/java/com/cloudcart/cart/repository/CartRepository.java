package com.cloudcart.cart.repository;

import com.cloudcart.cart.model.CartItem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

public class CartRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName = System.getenv("CART_TABLE");

    public CartRepository() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder();
        String endpointUrl = System.getenv("AWS_ENDPOINT_URL");
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        this.dynamoDbClient = builder.build();
    }

    public void addItem(CartItem item) {
        Map<String, AttributeValue> attributes = new HashMap<>();
        attributes.put("userId", AttributeValue.fromS(item.getUserId()));
        attributes.put("productId", AttributeValue.fromS(item.getProductId()));
        if (item.getTitle() != null) {
            attributes.put("title", AttributeValue.fromS(item.getTitle()));
        }
        attributes.put("quantity", AttributeValue.fromN(String.valueOf(item.getQuantity())));
        attributes.put("price", AttributeValue.fromN(String.valueOf(item.getPrice())));
        attributes.put("addedAt", AttributeValue.fromS(item.getAddedAt()));

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(attributes)
                .build();

        dynamoDbClient.putItem(request);
    }

    public List<CartItem> getCart(String userId) {
        Map<String, AttributeValue> keyCond = Map.of(":uid", AttributeValue.fromS(userId));

        QueryRequest query = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("userId = :uid")
                .expressionAttributeValues(keyCond)
                .build();

        QueryResponse result = dynamoDbClient.query(query);
        List<CartItem> items = new ArrayList<>();

        for (Map<String, AttributeValue> row : result.items()) {
            CartItem item = new CartItem();
            item.setUserId(row.get("userId").s());
            item.setProductId(row.get("productId").s());
            if (row.containsKey("title")) {
                item.setTitle(row.get("title").s());
            }
            item.setQuantity(Integer.parseInt(row.get("quantity").n()));
            item.setPrice(Double.parseDouble(row.get("price").n()));
            item.setAddedAt(row.get("addedAt").s());
            items.add(item);
        }

        return items;
    }

    public void removeItem(String userId, String productId) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("userId", AttributeValue.fromS(userId));
        key.put("productId", AttributeValue.fromS(productId));

        DeleteItemRequest request = DeleteItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build();

        dynamoDbClient.deleteItem(request);
    }

    public void updateQuantity(String userId, String productId, int quantity) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("userId", AttributeValue.fromS(userId));
        key.put("productId", AttributeValue.fromS(productId));

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression("SET quantity = :qty")
                .expressionAttributeValues(Map.of(":qty", AttributeValue.fromN(String.valueOf(quantity))))
                .build();

        dynamoDbClient.updateItem(request);
    }

    public void clearCart(String userId) {
        Map<String, AttributeValue> keyCond = Map.of(":uid", AttributeValue.fromS(userId));
        QueryRequest query = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("userId = :uid")
                .projectionExpression("productId")
                .expressionAttributeValues(keyCond)
                .build();
        QueryResponse result = dynamoDbClient.query(query);
        List<Map<String, AttributeValue>> allKeys = result.items().stream()
                .map(row -> {
                    Map<String, AttributeValue> k = new HashMap<>();
                    k.put("userId", AttributeValue.fromS(userId));
                    k.put("productId", row.get("productId"));
                    return k;
                })
                .collect(Collectors.toList());

        // BatchWriteItem allows at most 25 requests per call
        int batchSize = 25;
        for (int i = 0; i < allKeys.size(); i += batchSize) {
            List<Map<String, AttributeValue>> batch = allKeys.subList(i, Math.min(i + batchSize, allKeys.size()));
            List<WriteRequest> writeRequests = batch.stream()
                    .map(k -> WriteRequest.builder()
                            .deleteRequest(DeleteRequest.builder().key(k).build())
                            .build())
                    .collect(Collectors.toList());
            dynamoDbClient.batchWriteItem(BatchWriteItemRequest.builder()
                    .requestItems(Map.of(tableName, writeRequests))
                    .build());
        }
    }
}

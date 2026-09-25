package com.cloudcart.auth.repository;

import com.cloudcart.auth.model.User;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class DynamoUserRepository implements UserRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName = System.getenv("USERS_TABLE");

    public DynamoUserRepository() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder();
        String endpointUrl = System.getenv("AWS_ENDPOINT_URL");
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        this.dynamoDbClient = builder.build();
    }

    @Override
    public void createUser(User user) {
        Map<String, AttributeValue> attributes = new HashMap<>();
        attributes.put("email", AttributeValue.fromS(user.getEmail()));
        attributes.put("userId", AttributeValue.fromS(user.getUserId()));
        attributes.put("passwordHash", AttributeValue.fromS(user.getPasswordHash()));
        attributes.put("createdAt", AttributeValue.fromS(user.getCreatedAt()));

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(attributes)
                .conditionExpression("attribute_not_exists(email)")
                .build());
    }

    @Override
    public User getByEmail(String email) {
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("email", AttributeValue.fromS(email)))
                .build());

        if (!response.hasItem() || response.item().isEmpty()) {
            return null;
        }

        Map<String, AttributeValue> item = response.item();
        User user = new User();
        user.setEmail(item.get("email").s());
        user.setUserId(item.get("userId").s());
        user.setPasswordHash(item.get("passwordHash").s());
        user.setCreatedAt(item.get("createdAt").s());
        return user;
    }
}

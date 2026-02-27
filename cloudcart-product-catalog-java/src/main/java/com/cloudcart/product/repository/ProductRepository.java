package com.cloudcart.product.repository;

import com.cloudcart.product.model.Product;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ProductRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName = System.getenv("PRODUCTS_TABLE");

    public ProductRepository() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder();
        String endpointUrl = System.getenv("AWS_ENDPOINT_URL");
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        this.dynamoDbClient = builder.build();
    }
 
    public void saveProduct(Product product) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("productID", AttributeValue.fromS(product.getProductId()));
        item.put("title", AttributeValue.fromS(product.getTitle()));
        item.put("price", AttributeValue.fromN(String.valueOf(product.getPrice())));
        item.put("stock", AttributeValue.fromN(String.valueOf(product.getStock())));
        item.put("category", AttributeValue.fromS(product.getCategory()));
        item.put("imageUrl", AttributeValue.fromS(product.getImageUrl()));

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

        dynamoDbClient.putItem(request);
    }


public Product getProductById(String productId) {
    Map<String, AttributeValue> key = new HashMap<>();
    key.put("productID", AttributeValue.fromS(productId));

    GetItemRequest request = GetItemRequest.builder()
            .tableName(tableName)
            .key(key)
            .build();

    Map<String, AttributeValue> returnedItem = dynamoDbClient.getItem(request).item();

    if (returnedItem == null || returnedItem.isEmpty()) {
        return null;
    }

    Product product = new Product();
    product.setProductId(returnedItem.get("productID").s());
    product.setTitle(returnedItem.get("title").s());
    product.setPrice(Double.parseDouble(returnedItem.get("price").n()));
    product.setStock(Integer.parseInt(returnedItem.get("stock").n()));
    product.setCategory(returnedItem.get("category").s());
    product.setImageUrl(returnedItem.get("imageUrl").s());

    return product;
    }

    public Map<String, Object> getAllProducts(int limit, String lastEvaluatedKey) {
        List<Product> productList = new ArrayList<>();
        Map<String, Object> response = new HashMap<>();
    
        ScanRequest.Builder scanBuilder = ScanRequest.builder()
                .tableName(tableName)
                .limit(limit);
    
        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            scanBuilder.exclusiveStartKey(Map.of("productID", AttributeValue.fromS(lastEvaluatedKey)));
        }
    
        ScanResponse result = dynamoDbClient.scan(scanBuilder.build());
    
        for (Map<String, AttributeValue> item : result.items()) {
            Product product = new Product();
            product.setProductId(item.get("productID").s());
            product.setTitle(item.get("title").s());
            product.setPrice(Double.parseDouble(item.get("price").n()));
            product.setStock(Integer.parseInt(item.get("stock").n()));
            product.setCategory(item.get("category").s());
            product.setImageUrl(item.get("imageUrl").s());
            productList.add(product);
        }
    
        response.put("products", productList);
        if (result.hasLastEvaluatedKey()) {
            response.put("nextKey", result.lastEvaluatedKey().get("productID").s());
        }
    
        return response;
    }

    public void updateStock(String productId, int stock) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("productID", AttributeValue.fromS(productId));

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression("SET stock = :stockVal")
                .expressionAttributeValues(Map.of(":stockVal", AttributeValue.fromN(String.valueOf(stock))))
                .build();

        dynamoDbClient.updateItem(request);
    }

    // Returns true if reservation succeeded, false if insufficient stock.
    public boolean reserveStock(String productId, int qty) {
        try {
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("productID", AttributeValue.fromS(productId)))
                    .updateExpression("SET stock = stock - :qty")
                    .conditionExpression("stock >= :qty")
                    .expressionAttributeValues(Map.of(":qty", AttributeValue.fromN(String.valueOf(qty))))
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    public void releaseStock(String productId, int qty) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("productID", AttributeValue.fromS(productId)))
                .updateExpression("SET stock = stock + :qty")
                .expressionAttributeValues(Map.of(":qty", AttributeValue.fromN(String.valueOf(qty))))
                .build());
    }

}
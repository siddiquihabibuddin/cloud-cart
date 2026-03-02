package com.cloudcart.search.repository;

import com.cloudcart.search.model.ProductDocument;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProductScanRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public ProductScanRepository() {
        this.tableName = System.getenv("PRODUCTS_TABLE");
        DynamoDbClientBuilder builder = DynamoDbClient.builder();
        String endpointUrl = System.getenv("AWS_ENDPOINT_URL");
        if (endpointUrl != null && !endpointUrl.isBlank()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        this.dynamoDbClient = builder.build();
    }

    public List<ProductDocument> scanAll() {
        List<ProductDocument> results = new ArrayList<>();
        Map<String, AttributeValue> lastKey = null;

        do {
            ScanRequest.Builder scanBuilder = ScanRequest.builder().tableName(tableName);
            if (lastKey != null) {
                scanBuilder.exclusiveStartKey(lastKey);
            }
            ScanResponse response = dynamoDbClient.scan(scanBuilder.build());
            for (Map<String, AttributeValue> item : response.items()) {
                ProductDocument doc = new ProductDocument();
                doc.setProductId(attr(item, "productID"));
                doc.setTitle(attr(item, "title"));
                doc.setCategory(attr(item, "category"));
                doc.setImageUrl(attr(item, "imageUrl"));
                String priceStr = item.containsKey("price") ? item.get("price").n() : "0";
                String stockStr = item.containsKey("stock") ? item.get("stock").n() : "0";
                doc.setPrice(Double.parseDouble(priceStr));
                doc.setStock(Integer.parseInt(stockStr));
                results.add(doc);
            }
            lastKey = response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
        } while (lastKey != null);

        return results;
    }

    private String attr(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return (v != null && v.s() != null) ? v.s() : "";
    }
}

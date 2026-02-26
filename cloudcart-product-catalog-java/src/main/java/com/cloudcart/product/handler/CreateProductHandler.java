package com.cloudcart.product.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.product.model.Product;
import com.cloudcart.product.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

public class CreateProductHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ProductRepository repository = new ProductRepository();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            String body = (String) input.get("body");
            Product product = mapper.readValue(body, Product.class);
            product.setProductId(UUID.randomUUID().toString());
            repository.saveProduct(product);
            return response(201, "{\"id\":\"" + product.getProductId() + "\"}");
        } catch (Exception e) {
            context.getLogger().log("Error in CreateProductHandler: " + e.getMessage());
            return response(500, "{\"error\":\"Failed to create product\"}");
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

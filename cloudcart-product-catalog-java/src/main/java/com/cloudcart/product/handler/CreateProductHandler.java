package com.cloudcart.product.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.product.model.Product;
import com.cloudcart.product.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CreateProductHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ProductRepository REPOSITORY = new ProductRepository();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            String body = (String) input.get("body");
            Product product = MAPPER.readValue(body, Product.class);

            List<String> errors = new ArrayList<>();
            if (product.getTitle() == null || product.getTitle().isBlank()) {
                errors.add("title is required");
            }
            if (product.getPrice() < 0) {
                errors.add("price must be >= 0");
            }
            if (product.getStock() < 0) {
                errors.add("stock must be >= 0");
            }
            if (!errors.isEmpty()) {
                return response(400, MAPPER.writeValueAsString(
                        Map.of("error", "Validation failed", "details", errors)));
            }

            product.setProductId(UUID.randomUUID().toString());
            REPOSITORY.saveProduct(product);
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

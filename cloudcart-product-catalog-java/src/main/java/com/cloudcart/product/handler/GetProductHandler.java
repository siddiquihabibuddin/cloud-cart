package com.cloudcart.product.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.product.model.Product;
import com.cloudcart.product.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class GetProductHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final ProductRepository repository = new ProductRepository();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            Map<String, String> pathParams = (Map<String, String>) input.get("pathParameters");
            String productId = pathParams.get("id");
            Product product = repository.getProductById(productId);
            if (product == null) {
                return response(404, "{\"error\":\"Product not found\"}");
            }
            return response(200, mapper.writeValueAsString(product));
        } catch (Exception e) {
            context.getLogger().log("Error in GetProductHandler: " + e.getMessage());
            return response(500, "{\"error\":\"Failed to get product\"}");
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

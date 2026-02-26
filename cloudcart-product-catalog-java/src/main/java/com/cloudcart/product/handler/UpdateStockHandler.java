package com.cloudcart.product.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.product.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class UpdateStockHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final ProductRepository repository = new ProductRepository();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            Map<String, String> pathParams = (Map<String, String>) input.get("pathParameters");
            String productId = pathParams.get("id");

            String body = (String) input.get("body");
            Map<String, Object> bodyMap = mapper.readValue(body, Map.class);
            int stock = ((Number) bodyMap.get("stock")).intValue();

            repository.updateStock(productId, stock);
            return response(200, "{\"message\":\"Stock updated successfully\"}");
        } catch (Exception e) {
            context.getLogger().log("Error in UpdateStockHandler: " + e.getMessage());
            return response(500, "{\"error\":\"Failed to update stock\"}");
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

package com.cloudcart.cart.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.cart.repository.CartRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class UpdateQuantityHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final CartRepository REPOSITORY = new CartRepository();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            Map<String, String> pathParams = (Map<String, String>) input.get("pathParameters");
            String userId = pathParams.get("userId");
            String productId = pathParams.get("productId");

            String body = (String) input.get("body");
            Map<String, Object> bodyMap = MAPPER.readValue(body, Map.class);

            Object quantityObj = bodyMap.get("quantity");
            if (quantityObj == null) {
                return response(400, "{\"error\":\"quantity is required\"}");
            }
            int quantity = ((Number) quantityObj).intValue();
            if (quantity < 1) {
                return response(400, "{\"error\":\"quantity must be >= 1\"}");
            }

            REPOSITORY.updateQuantity(userId, productId, quantity);
            return response(200, "{\"message\":\"Quantity updated\"}");
        } catch (Exception e) {
            context.getLogger().log("Error in UpdateQuantityHandler: " + e.getMessage());
            return response(500, "{\"error\":\"Failed to update quantity\"}");
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

package com.cloudcart.cart.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.cart.repository.CartRepository;

import java.util.Map;

public class ClearCartHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final CartRepository REPOSITORY = new CartRepository();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            Map<?, ?> pathParameters = (Map<?, ?>) input.get("pathParameters");
            if (pathParameters == null) {
                return response(400, "{\"error\":\"userId is required\"}");
            }
            String userId = (String) pathParameters.get("userId");
            if (userId == null || userId.isBlank()) {
                return response(400, "{\"error\":\"userId is required\"}");
            }

            REPOSITORY.clearCart(userId);
            return Map.of(
                "statusCode", 204,
                "headers", Map.of("Content-Type", "application/json"),
                "body", ""
            );
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return response(500, "{\"error\":\"Failed to clear cart\"}");
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

package com.cloudcart.cart.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.cart.repository.CartRepository;
import com.cloudcart.cart.util.JwtVerificationException;
import com.cloudcart.cart.util.JwtVerifier;

import java.util.Map;

public class RemoveFromCartHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final CartRepository repository = new CartRepository();
    private final JwtVerifier jwtVerifier = new JwtVerifier(System.getenv("JWT_SECRET"));

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            String authenticatedUserId;
            try {
                authenticatedUserId = jwtVerifier.verifyFromHeaders((Map<String, Object>) input.get("headers"));
            } catch (JwtVerificationException e) {
                return response(401, "{\"error\":\"Unauthorized\"}");
            }

            Map<String, String> pathParams = (Map<String, String>) input.get("pathParameters");
            String userId = pathParams.get("userId");
            String productId = pathParams.get("productId");
            if (!authenticatedUserId.equals(userId)) {
                return response(403, "{\"error\":\"Forbidden\"}");
            }

            repository.removeItem(userId, productId);
            return response(200, "{\"message\":\"Item removed from cart\"}");
        } catch (Exception e) {
            context.getLogger().log("Error in RemoveFromCartHandler: " + e.getMessage());
            return response(500, "{\"error\":\"Failed to remove item from cart\"}");
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

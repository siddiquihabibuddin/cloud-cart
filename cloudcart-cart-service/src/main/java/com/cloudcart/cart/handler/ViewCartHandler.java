package com.cloudcart.cart.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.cart.model.CartItem;
import com.cloudcart.cart.repository.CartRepository;
import com.cloudcart.cart.util.JwtVerificationException;
import com.cloudcart.cart.util.JwtVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class ViewCartHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final ObjectMapper mapper = new ObjectMapper();
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
            if (!authenticatedUserId.equals(userId)) {
                return response(403, "{\"error\":\"Forbidden\"}");
            }
            List<CartItem> items = repository.getCart(userId);
            return response(200, mapper.writeValueAsString(items));
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return response(500, "{\"error\":\"Failed to get cart\"}");
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

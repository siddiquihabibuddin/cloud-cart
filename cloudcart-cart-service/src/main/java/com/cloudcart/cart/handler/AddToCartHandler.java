package com.cloudcart.cart.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.cart.model.CartItem;
import com.cloudcart.cart.repository.CartRepository;
import com.cloudcart.cart.util.JwtVerificationException;
import com.cloudcart.cart.util.JwtVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AddToCartHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final CartRepository REPOSITORY = new CartRepository();
    private static final JwtVerifier JWT_VERIFIER = new JwtVerifier(System.getenv("JWT_SECRET"));

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            String authenticatedUserId;
            try {
                authenticatedUserId = JWT_VERIFIER.verifyFromHeaders((Map<String, Object>) input.get("headers"));
            } catch (JwtVerificationException e) {
                return response(401, "{\"error\":\"Unauthorized\"}");
            }

            String body = (String) input.get("body");
            CartItem item = MAPPER.readValue(body, CartItem.class);

            List<String> errors = new ArrayList<>();
            if (item.getUserId() == null || item.getUserId().isBlank()) {
                errors.add("userId is required");
            }
            if (item.getProductId() == null || item.getProductId().isBlank()) {
                errors.add("productId is required");
            }
            if (item.getTitle() == null || item.getTitle().isBlank()) {
                errors.add("title is required");
            }
            if (item.getQuantity() < 1) {
                errors.add("quantity must be >= 1");
            }
            if (item.getPrice() < 0) {
                errors.add("price must be >= 0");
            }
            if (!errors.isEmpty()) {
                return response(400, MAPPER.writeValueAsString(
                        Map.of("error", "Validation failed", "details", errors)));
            }
            if (!authenticatedUserId.equals(item.getUserId())) {
                return response(403, "{\"error\":\"Forbidden\"}");
            }

            item.setAddedAt(java.time.Instant.now().toString());
            REPOSITORY.addItem(item);
            return response(200, "{\"message\":\"Item added to cart\"}");
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return response(500, "{\"error\":\"Failed to add item to cart\"}");
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

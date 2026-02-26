package com.cloudcart.cart.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.cart.model.CartItem;
import com.cloudcart.cart.repository.CartRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class AddToCartHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CartRepository repository = new CartRepository();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            String body = (String) input.get("body");
            CartItem item = mapper.readValue(body, CartItem.class);
            item.setAddedAt(java.time.Instant.now().toString());
            repository.addItem(item);
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

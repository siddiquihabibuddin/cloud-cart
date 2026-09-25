package com.cloudcart.order.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.order.model.Order;
import com.cloudcart.order.repository.OrderRepository;
import com.cloudcart.order.util.JwtVerificationException;
import com.cloudcart.order.util.JwtVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class ListOrdersHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OrderRepository repository = new OrderRepository();
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

            Map<String, String> queryParams = (Map<String, String>) input.get("queryStringParameters");
            if (queryParams == null || queryParams.get("userId") == null) {
                return response(400, "{\"error\":\"userId query parameter is required\"}");
            }

            String userId = queryParams.get("userId");
            if (!authenticatedUserId.equals(userId)) {
                return response(403, "{\"error\":\"Forbidden\"}");
            }
            List<Order> orders = repository.listByUser(userId);

            return Map.of(
                    "statusCode", 200,
                    "headers", Map.of("Content-Type", "application/json"),
                    "body", mapper.writeValueAsString(orders)
            );
        } catch (Exception e) {
            context.getLogger().log("Error listing orders: " + e.getMessage());
            return response(500, "{\"error\":\"Failed to list orders\"}");
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

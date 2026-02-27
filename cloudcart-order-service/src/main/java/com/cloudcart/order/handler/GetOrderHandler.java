package com.cloudcart.order.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.order.model.Order;
import com.cloudcart.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class GetOrderHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final OrderRepository REPOSITORY = new OrderRepository();

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            Map<String, String> pathParams = (Map<String, String>) input.get("pathParameters");
            if (pathParams == null || pathParams.get("orderId") == null) {
                return response(400, "{\"error\":\"orderId path parameter is required\"}");
            }

            Map<String, String> queryParams = (Map<String, String>) input.get("queryStringParameters");
            String requestingUserId = queryParams != null ? queryParams.get("userId") : null;
            if (requestingUserId == null || requestingUserId.isBlank()) {
                return response(400, "{\"error\":\"userId query parameter is required\"}");
            }

            String orderId = pathParams.get("orderId");
            Order order = REPOSITORY.getOrder(orderId);

            if (order == null) {
                return response(404, "{\"error\":\"Order not found\"}");
            }

            if (!requestingUserId.equals(order.getUserId())) {
                return response(403, "{\"error\":\"Access denied\"}");
            }

            return Map.of(
                    "statusCode", 200,
                    "headers", Map.of("Content-Type", "application/json"),
                    "body", MAPPER.writeValueAsString(order)
            );
        } catch (Exception e) {
            context.getLogger().log("Error getting order: " + e.getMessage());
            return response(500, "{\"error\":\"Failed to retrieve order\"}");
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

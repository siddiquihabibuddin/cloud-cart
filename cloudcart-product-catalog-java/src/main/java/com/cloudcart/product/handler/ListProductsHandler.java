package com.cloudcart.product.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.product.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

public class ListProductsHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ProductRepository REPOSITORY = new ProductRepository();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            Map<String, String> queryParams = (Map<String, String>) input.get("queryStringParameters");

            int limit = 10;
            String lastKey = null;

            if (queryParams != null) {
                if (queryParams.containsKey("limit")) {
                    String limitStr = queryParams.get("limit");
                    try {
                        limit = Integer.parseInt(limitStr);
                    } catch (NumberFormatException e) {
                        return response(400, "{\"error\":\"limit must be a numeric value\"}");
                    }
                    limit = Math.max(1, Math.min(limit, 100));
                }
                if (queryParams.containsKey("lastKey")) {
                    lastKey = queryParams.get("lastKey");
                }
            }

            Map<String, Object> result = REPOSITORY.getAllProducts(limit, lastKey);
            return response(200, MAPPER.writeValueAsString(result));
        } catch (Exception e) {
            context.getLogger().log("Error in ListProductsHandler: " + e.getMessage());
            return response(500, "{\"error\":\"Unable to list products\"}");
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

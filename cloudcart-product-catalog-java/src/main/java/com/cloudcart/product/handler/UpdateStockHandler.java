package com.cloudcart.product.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.product.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class UpdateStockHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ProductRepository REPOSITORY = new ProductRepository();

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            Map<String, String> pathParams = (Map<String, String>) input.get("pathParameters");
            String productId = pathParams.get("id");

            String body = (String) input.get("body");
            Map<String, Object> bodyMap = MAPPER.readValue(body, Map.class);

            if (bodyMap.containsKey("reserve")) {
                int qty = ((Number) bodyMap.get("reserve")).intValue();
                if (qty < 1) return response(400, "{\"error\":\"reserve quantity must be >= 1\"}");
                boolean ok = REPOSITORY.reserveStock(productId, qty);
                if (!ok) return response(409, "{\"error\":\"Insufficient stock\"}");
                return response(200, "{\"message\":\"Stock reserved\"}");

            } else if (bodyMap.containsKey("release")) {
                int qty = ((Number) bodyMap.get("release")).intValue();
                if (qty < 1) return response(400, "{\"error\":\"release quantity must be >= 1\"}");
                REPOSITORY.releaseStock(productId, qty);
                return response(200, "{\"message\":\"Stock released\"}");

            } else if (bodyMap.containsKey("stock")) {
                int stock = ((Number) bodyMap.get("stock")).intValue();
                if (stock < 0) return response(400, "{\"error\":\"stock must be >= 0\"}");
                REPOSITORY.updateStock(productId, stock);
                return response(200, "{\"message\":\"Stock updated successfully\"}");

            } else {
                return response(400, "{\"error\":\"Body must contain 'reserve', 'release', or 'stock'\"}");
            }
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

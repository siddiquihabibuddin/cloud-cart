package com.cloudcart.search.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.search.model.ProductDocument;
import com.cloudcart.search.repository.OpenSearchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class SearchProductsHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final OpenSearchRepository REPO = new OpenSearchRepository();

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            Map<String, String> params = (Map<String, String>) event.get("queryStringParameters");
            if (params == null || params.get("q") == null || params.get("q").isBlank()) {
                return response(400, "{\"error\":\"query parameter 'q' is required\"}");
            }
            String query = params.get("q");
            int limit = 20;
            if (params.containsKey("limit")) {
                try {
                    limit = Integer.parseInt(params.get("limit"));
                    limit = Math.max(1, Math.min(100, limit));
                } catch (NumberFormatException ignored) {}
            }

            List<ProductDocument> results = REPO.search(query, limit);
            String body = MAPPER.writeValueAsString(Map.of(
                "products", results,
                "total", results.size()
            ));
            return response(200, body);
        } catch (Exception e) {
            context.getLogger().log("SearchProductsHandler error: " + e.getMessage());
            return response(500, "{\"error\":\"Search failed\"}");
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

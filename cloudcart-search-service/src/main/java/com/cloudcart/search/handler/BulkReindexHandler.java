package com.cloudcart.search.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.cloudcart.common.handler.LoggedHandler;
import com.cloudcart.search.model.ProductDocument;
import com.cloudcart.search.repository.OpenSearchRepository;
import com.cloudcart.search.repository.ProductScanRepository;

import java.util.List;
import java.util.Map;

public class BulkReindexHandler extends LoggedHandler {

    private static final OpenSearchRepository SEARCH_REPO = new OpenSearchRepository();
    private static final ProductScanRepository SCAN_REPO = new ProductScanRepository();

    @Override
    protected Map<String, Object> process(Map<String, Object> event, Context context) {
        try {
            SEARCH_REPO.ensureIndex();
            List<ProductDocument> products = SCAN_REPO.scanAll();
            SEARCH_REPO.bulkIndex(products);
            return response(200, "{\"indexed\":" + products.size() + "}");
        } catch (Exception e) {
            context.getLogger().log("BulkReindexHandler error: " + e.getMessage());
            return response(500, "{\"error\":\"Reindex failed: " + e.getMessage().replace("\"", "'") + "\"}");
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

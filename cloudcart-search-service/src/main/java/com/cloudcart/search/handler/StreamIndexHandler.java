package com.cloudcart.search.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.search.model.ProductDocument;
import com.cloudcart.search.repository.OpenSearchRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StreamIndexHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final OpenSearchRepository REPO = new OpenSearchRepository();

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        List<Map<String, String>> batchItemFailures = new ArrayList<>();

        List<Map<String, Object>> records = (List<Map<String, Object>>) event.get("Records");
        if (records == null) return Map.of("batchItemFailures", batchItemFailures);

        for (Map<String, Object> record : records) {
            String eventName = (String) record.get("eventName");
            Map<String, Object> dynamo = (Map<String, Object>) record.get("dynamodb");
            String seqNum = (String) dynamo.get("SequenceNumber");

            try {
                if ("REMOVE".equals(eventName)) {
                    Map<String, Object> keys = (Map<String, Object>) dynamo.get("Keys");
                    String productId = (String) ((Map<?, ?>) keys.get("productID")).get("S");
                    REPO.deleteDocument(productId);
                } else {
                    Map<String, Object> image = (Map<String, Object>) dynamo.get("NewImage");
                    ProductDocument doc = parseImage(image);
                    REPO.indexDocument(doc);
                }
            } catch (Exception e) {
                context.getLogger().log("Failed to process record " + seqNum + ": " + e.getMessage());
                batchItemFailures.add(Map.of("itemIdentifier", seqNum));
            }
        }

        return Map.of("batchItemFailures", batchItemFailures);
    }

    @SuppressWarnings("unchecked")
    private ProductDocument parseImage(Map<String, Object> image) {
        ProductDocument doc = new ProductDocument();
        doc.setProductId(getS(image, "productID"));
        doc.setTitle(getS(image, "title"));
        doc.setCategory(getS(image, "category"));
        doc.setImageUrl(getS(image, "imageUrl"));
        String priceN = getN(image, "price");
        String stockN = getN(image, "stock");
        doc.setPrice(priceN.isEmpty() ? 0.0 : Double.parseDouble(priceN));
        doc.setStock(stockN.isEmpty() ? 0 : Integer.parseInt(stockN));
        return doc;
    }

    @SuppressWarnings("unchecked")
    private String getS(Map<String, Object> image, String key) {
        Object field = image.get(key);
        if (field == null) return "";
        Object s = ((Map<?, ?>) field).get("S");
        return s != null ? (String) s : "";
    }

    @SuppressWarnings("unchecked")
    private String getN(Map<String, Object> image, String key) {
        Object field = image.get(key);
        if (field == null) return "";
        Object n = ((Map<?, ?>) field).get("N");
        return n != null ? (String) n : "";
    }
}

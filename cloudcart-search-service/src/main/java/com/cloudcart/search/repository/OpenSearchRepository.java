package com.cloudcart.search.repository;

import com.cloudcart.search.model.ProductDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class OpenSearchRepository {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private final String endpoint;

    public OpenSearchRepository() {
        String ep = System.getenv("OPENSEARCH_ENDPOINT");
        this.endpoint = (ep != null && !ep.isBlank()) ? ep : "http://localhost:9200";
        // ensureIndex() intentionally NOT called here — it makes a blocking HTTP call
        // that would stall Lambda cold start if OpenSearch is slow. Call it explicitly
        // from BulkReindexHandler before indexing.
    }

    public void ensureIndex() {
        try {
            String mapping = """
                {
                  "mappings": {
                    "properties": {
                      "productId": { "type": "keyword" },
                      "title":     { "type": "text" },
                      "category":  { "type": "text" },
                      "imageUrl":  { "type": "keyword" },
                      "price":     { "type": "double" },
                      "stock":     { "type": "integer" }
                    }
                  }
                }
                """;
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/products"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapping))
                .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            // 200 = created, 400 with resource_already_exists_exception is fine
            if (resp.statusCode() != 200 && resp.statusCode() != 400) {
                System.err.println("ensureIndex unexpected status: " + resp.statusCode() + " " + resp.body());
            }
        } catch (Exception e) {
            System.err.println("ensureIndex error: " + e.getMessage());
        }
    }

    public void indexDocument(ProductDocument doc) throws Exception {
        String json = MAPPER.writeValueAsString(doc);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(endpoint + "/products/_doc/" + doc.getProductId()))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .build();
        HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200 && resp.statusCode() != 201) {
            throw new RuntimeException("indexDocument failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    public void deleteDocument(String productId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(endpoint + "/products/_doc/" + productId))
            .DELETE()
            .build();
        HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        // 200 or 404 both acceptable
        if (resp.statusCode() != 200 && resp.statusCode() != 404) {
            throw new RuntimeException("deleteDocument failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    public List<ProductDocument> search(String query, int size) throws Exception {
        // Build query via Jackson to prevent JSON/query injection — never interpolate
        // user input directly into a raw JSON string.
        ObjectNode multiMatch = MAPPER.createObjectNode()
            .put("query", query)
            .put("type", "best_fields");
        multiMatch.putArray("fields").add("title^2").add("category");

        ObjectNode body = MAPPER.createObjectNode();
        body.putObject("query").set("multi_match", multiMatch);
        body.put("size", size);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(endpoint + "/products/_search"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
            .build();
        HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("search failed: " + resp.statusCode() + " " + resp.body());
        }
        JsonNode root = MAPPER.readTree(resp.body());
        JsonNode hits = root.path("hits").path("hits");
        List<ProductDocument> results = new ArrayList<>();
        for (JsonNode hit : hits) {
            ProductDocument doc = MAPPER.treeToValue(hit.path("_source"), ProductDocument.class);
            results.add(doc);
        }
        return results;
    }

    public void bulkIndex(List<ProductDocument> docs) throws Exception {
        if (docs.isEmpty()) return;
        StringBuilder ndjson = new StringBuilder();
        for (ProductDocument doc : docs) {
            ndjson.append("{\"index\":{\"_index\":\"products\",\"_id\":\"").append(doc.getProductId()).append("\"}}\n");
            ndjson.append(MAPPER.writeValueAsString(doc)).append("\n");
        }
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(endpoint + "/_bulk"))
            .header("Content-Type", "application/x-ndjson")
            .POST(HttpRequest.BodyPublishers.ofString(ndjson.toString()))
            .build();
        HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("bulkIndex failed: " + resp.statusCode() + " " + resp.body());
        }
        // _bulk always returns HTTP 200 even when individual operations fail.
        // Parse the "errors" flag and collect failed document IDs.
        JsonNode bulkResp = MAPPER.readTree(resp.body());
        if (bulkResp.path("errors").asBoolean(false)) {
            List<String> failed = new ArrayList<>();
            for (JsonNode item : bulkResp.path("items")) {
                JsonNode indexResult = item.path("index");
                if (indexResult.has("error")) {
                    failed.add(indexResult.path("_id").asText("unknown"));
                }
            }
            throw new RuntimeException("bulkIndex had " + failed.size() + " document failures: " + failed);
        }
    }
}

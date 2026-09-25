package com.cloudcart.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ProductTools {

    private final RestClient restClient;

    public ProductTools(RestClient cloudCartRestClient) {
        this.restClient = cloudCartRestClient;
    }

    @Tool(name = "search_products", description = "Search the product catalog by free-text query, optionally capped at a max price.")
    public Map<String, Object> searchProducts(
            @ToolParam(description = "Search text, e.g. 'wireless mouse'") String query,
            @ToolParam(description = "Optional maximum price filter", required = false) Double maxPrice) {
        try {
            Map<String, Object> result = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search").queryParam("q", query).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (maxPrice == null || result == null) {
                return result;
            }
            Object productsObj = result.get("products");
            if (!(productsObj instanceof List<?> products)) {
                return result;
            }
            List<Object> filtered = new ArrayList<>();
            for (Object p : products) {
                if (p instanceof Map<?, ?> product) {
                    Object price = product.get("price");
                    if (price instanceof Number number && number.doubleValue() <= maxPrice) {
                        filtered.add(product);
                    }
                }
            }
            return Map.of("products", filtered, "total", filtered.size());
        } catch (Exception e) {
            return ToolErrors.from(e);
        }
    }

    @Tool(name = "get_product", description = "Get full details for a single product by id.")
    public Map<String, Object> getProduct(@ToolParam(description = "The product id") String productId) {
        try {
            return restClient.get()
                    .uri("/products/{id}", productId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return ToolErrors.from(e);
        }
    }
}

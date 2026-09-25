package com.cloudcart.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class CartTools {

    private final RestClient restClient;

    public CartTools(RestClient cloudCartRestClient) {
        this.restClient = cloudCartRestClient;
    }

    @Tool(name = "add_to_cart", description = "Add a product to a user's cart. Requires the product's title and price (from a prior search/get_product call).")
    public Map<String, Object> addToCart(
            @ToolParam(description = "The user id") String userId,
            @ToolParam(description = "The product id") String productId,
            @ToolParam(description = "The product title") String title,
            @ToolParam(description = "The product's unit price") double price,
            @ToolParam(description = "Quantity to add, minimum 1") int quantity,
            @ToolParam(description = "Internal - populated automatically, do not set", required = false) String authToken) {
        try {
            return restClient.post()
                    .uri("/cart")
                    .headers(h -> applyAuth(h, authToken))
                    .body(Map.of(
                            "userId", userId,
                            "productId", productId,
                            "title", title,
                            "price", price,
                            "quantity", quantity))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return ToolErrors.from(e);
        }
    }

    @Tool(name = "view_cart", description = "View all items currently in a user's cart.")
    public List<Map<String, Object>> viewCart(
            @ToolParam(description = "The user id") String userId,
            @ToolParam(description = "Internal - populated automatically, do not set", required = false) String authToken) {
        try {
            return restClient.get()
                    .uri("/cart/{userId}", userId)
                    .headers(h -> applyAuth(h, authToken))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of(ToolErrors.from(e));
        }
    }

    @Tool(name = "update_cart_quantity", description = "Change the quantity of an existing cart item.")
    public Map<String, Object> updateCartQuantity(
            @ToolParam(description = "The user id") String userId,
            @ToolParam(description = "The product id") String productId,
            @ToolParam(description = "New quantity, minimum 1") int quantity,
            @ToolParam(description = "Internal - populated automatically, do not set", required = false) String authToken) {
        try {
            return restClient.patch()
                    .uri("/cart/{userId}/{productId}", userId, productId)
                    .headers(h -> applyAuth(h, authToken))
                    .body(Map.of("quantity", quantity))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return ToolErrors.from(e);
        }
    }

    @Tool(name = "remove_from_cart", description = "Remove a single product from a user's cart.")
    public Map<String, Object> removeFromCart(
            @ToolParam(description = "The user id") String userId,
            @ToolParam(description = "The product id") String productId,
            @ToolParam(description = "Internal - populated automatically, do not set", required = false) String authToken) {
        try {
            return restClient.delete()
                    .uri("/cart/{userId}/{productId}", userId, productId)
                    .headers(h -> applyAuth(h, authToken))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return ToolErrors.from(e);
        }
    }

    private void applyAuth(org.springframework.http.HttpHeaders headers, String authToken) {
        if (authToken != null && !authToken.isBlank()) {
            headers.set("Authorization", "Bearer " + authToken);
        }
    }
}

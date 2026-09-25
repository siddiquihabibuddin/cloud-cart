package com.cloudcart.mcp.tool;

import com.cloudcart.mcp.config.CloudCartProperties;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class OrderTools {

    private final RestClient restClient;
    private final String orderApiKey;

    public OrderTools(RestClient cloudCartRestClient, CloudCartProperties properties) {
        this.restClient = cloudCartRestClient;
        this.orderApiKey = properties.orderApiKey();
    }

    @Tool(name = "place_order", description = "Place an order for a list of items. Each item needs productId, quantity, and price.")
    public Map<String, Object> placeOrder(
            @ToolParam(description = "The user id") String userId,
            @ToolParam(description = "Items to order") List<OrderItemInput> items,
            @ToolParam(description = "Internal - populated automatically, do not set", required = false) String authToken) {
        try {
            return restClient.post()
                    .uri("/orders")
                    .headers(h -> applyAuth(h, authToken))
                    .body(Map.of("userId", userId, "items", items))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return ToolErrors.from(e);
        }
    }

    @Tool(name = "get_order", description = "Get the status and details of a single order.")
    public Map<String, Object> getOrder(
            @ToolParam(description = "The order id") String orderId,
            @ToolParam(description = "The user id who owns the order") String userId,
            @ToolParam(description = "Internal - populated automatically, do not set", required = false) String authToken) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/orders/{orderId}").queryParam("userId", userId).build(orderId))
                    .headers(h -> applyAuth(h, authToken))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return ToolErrors.from(e);
        }
    }

    @Tool(name = "list_orders", description = "List all orders placed by a user.")
    public List<Map<String, Object>> listOrders(
            @ToolParam(description = "The user id") String userId,
            @ToolParam(description = "Internal - populated automatically, do not set", required = false) String authToken) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/orders").queryParam("userId", userId).build())
                    .headers(h -> applyAuth(h, authToken))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of(ToolErrors.from(e));
        }
    }

    private void applyAuth(HttpHeaders headers, String authToken) {
        headers.set("x-api-key", orderApiKey);
        if (authToken != null && !authToken.isBlank()) {
            headers.set("Authorization", "Bearer " + authToken);
        }
    }
}

package com.cloudcart.order.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.order.model.Order;
import com.cloudcart.order.model.OrderItem;
import com.cloudcart.order.model.OrderPlacedEvent;
import com.cloudcart.order.repository.OrderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlaceOrderHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OrderRepository repository = new OrderRepository();
    private final SqsClient sqsClient;
    private final String queueUrl = System.getenv("ORDER_QUEUE_URL");

    public PlaceOrderHandler() {
        SqsClientBuilder builder = SqsClient.builder();
        String endpointUrl = System.getenv("AWS_ENDPOINT_URL");
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        this.sqsClient = builder.build();
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            String body = (String) input.get("body");
            Map<String, Object> requestBody = mapper.readValue(body, new TypeReference<>() {});

            String userId = (String) requestBody.get("userId");
            List<OrderItem> items = mapper.convertValue(
                    requestBody.get("items"),
                    new TypeReference<List<OrderItem>>() {}
            );

            if (userId == null || items == null || items.isEmpty()) {
                return response(400, "{\"error\":\"userId and items are required\"}");
            }

            double total = items.stream()
                    .mapToDouble(i -> i.getPrice() * i.getQuantity())
                    .sum();

            String orderId = UUID.randomUUID().toString();
            String itemsJson = mapper.writeValueAsString(items);

            Order order = new Order();
            order.setOrderId(orderId);
            order.setUserId(userId);
            order.setItemsJson(itemsJson);
            order.setTotalAmount(total);
            order.setStatus("PENDING");
            order.setCreatedAt(Instant.now().toString());

            repository.saveOrder(order);

            OrderPlacedEvent event = new OrderPlacedEvent(orderId, userId, items, total);
            String eventJson = mapper.writeValueAsString(event);

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(eventJson)
                    .build());

            context.getLogger().log("Order placed: " + orderId + " for user: " + userId);

            String responseBody = mapper.writeValueAsString(Map.of("orderId", orderId));
            return Map.of(
                    "statusCode", 201,
                    "headers", Map.of("Content-Type", "application/json"),
                    "body", responseBody
            );
        } catch (Exception e) {
            context.getLogger().log("Error placing order: " + e.getMessage());
            return response(500, "{\"error\":\"Failed to place order\"}");
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

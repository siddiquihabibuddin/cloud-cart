package com.cloudcart.order.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.order.model.Order;
import com.cloudcart.order.model.OrderItem;
import com.cloudcart.order.model.OrderPlacedEvent;
import com.cloudcart.order.repository.OrderRepository;
import com.cloudcart.order.util.JsonLogger;
import com.cloudcart.order.util.MetricsEmitter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlaceOrderHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final OrderRepository REPOSITORY = new OrderRepository();
    private static final MetricsEmitter METRICS = new MetricsEmitter("CloudCart/Orders");
    private static final SqsClient SQS_CLIENT;
    private static final DynamoDbClient DYNAMO_CLIENT;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final String QUEUE_URL = System.getenv("ORDER_QUEUE_URL");
    private static final String PRODUCTS_API_URL = System.getenv("PRODUCTS_API_URL");
    private static final String IDEMPOTENCY_TABLE = System.getenv("IDEMPOTENCY_TABLE");

    static {
        String endpointUrl = System.getenv("AWS_ENDPOINT_URL");
        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .retryPolicy(RetryPolicy.builder().numRetries(3).build())
                .build();

        SqsClientBuilder sqsBuilder = SqsClient.builder().overrideConfiguration(overrideConfig);
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            sqsBuilder.endpointOverride(URI.create(endpointUrl));
        }
        SQS_CLIENT = sqsBuilder.build();

        DynamoDbClientBuilder dynamoBuilder = DynamoDbClient.builder().overrideConfiguration(overrideConfig);
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            dynamoBuilder.endpointOverride(URI.create(endpointUrl));
        }
        DYNAMO_CLIENT = dynamoBuilder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        Map<String, Object> headers = (Map<String, Object>) input.get("headers");
        JsonLogger logger = JsonLogger.fromHeaders("order-service", headers);

        // Declare idempotencyKey outside the try block so the catch clause can
        // reference it when marking the idempotency record FAILED.
        String idempotencyKey = null;

        try {
            // --- Input parsing ---
            String body = (String) input.get("body");
            Map<String, Object> requestBody = MAPPER.readValue(body, new TypeReference<>() {});

            String userId = (String) requestBody.get("userId");
            List<OrderItem> items = MAPPER.convertValue(
                    requestBody.get("items"),
                    new TypeReference<List<OrderItem>>() {}
            );

            // --- Input validation ---
            if (userId == null || userId.isBlank()) {
                return response(400, "{\"error\":\"userId is required\"}");
            }
            if (items == null || items.isEmpty()) {
                return response(400, "{\"error\":\"items are required\"}");
            }
            List<String> validationErrors = new ArrayList<>();
            for (OrderItem item : items) {
                if (item.getProductId() == null || item.getProductId().isBlank()) {
                    validationErrors.add("productId is required for each item");
                }
                if (item.getQuantity() < 1) {
                    validationErrors.add("quantity must be >= 1 for productId: " + item.getProductId());
                }
                if (item.getPrice() < 0) {
                    validationErrors.add("price must be >= 0 for productId: " + item.getProductId());
                }
            }
            if (!validationErrors.isEmpty()) {
                return response(400, MAPPER.writeValueAsString(
                        Map.of("error", "Validation failed", "details", validationErrors)));
            }

            // --- Idempotency: atomically claim the key before doing any work ---
            // Uses conditional PutItem (attribute_not_exists) so only one concurrent
            // request can win the slot — eliminates the GetItem→PutItem TOCTOU race.
            idempotencyKey = extractHeader(headers, "Idempotency-Key");
            if (idempotencyKey != null && IDEMPOTENCY_TABLE != null) {
                Map<String, AttributeValue> claimItem = new HashMap<>();
                claimItem.put("idempotencyKey", AttributeValue.fromS(idempotencyKey));
                claimItem.put("status", AttributeValue.fromS("IN_PROGRESS"));
                claimItem.put("expiresAt", AttributeValue.fromN(
                        String.valueOf(Instant.now().getEpochSecond() + 86400)));
                try {
                    DYNAMO_CLIENT.putItem(PutItemRequest.builder()
                            .tableName(IDEMPOTENCY_TABLE)
                            .item(claimItem)
                            .conditionExpression("attribute_not_exists(idempotencyKey)")
                            .build());
                    // Slot claimed — fall through to process the order
                } catch (ConditionalCheckFailedException e) {
                    // Key already exists; fetch the stored outcome
                    GetItemResponse existing = DYNAMO_CLIENT.getItem(GetItemRequest.builder()
                            .tableName(IDEMPOTENCY_TABLE)
                            .key(Map.of("idempotencyKey", AttributeValue.fromS(idempotencyKey)))
                            .build());
                    if (existing.hasItem() && !existing.item().isEmpty()) {
                        Map<String, AttributeValue> stored = existing.item();
                        String storedStatus = stored.get("status").s();
                        if ("COMPLETED".equals(storedStatus)) {
                            int cachedStatus = Integer.parseInt(stored.get("statusCode").n());
                            String cachedBody = stored.get("responseBody").s();
                            logger.info("Idempotent response returned",
                                    Map.of("idempotencyKey", idempotencyKey));
                            return response(cachedStatus, cachedBody);
                        }
                        if ("IN_PROGRESS".equals(storedStatus)) {
                            // A concurrent request is still running — tell the client to back off
                            return response(409, "{\"error\":\"A request with this Idempotency-Key is already in progress\"}");
                        }
                        // FAILED: previous attempt failed after claiming the slot.
                        // Delete the stale record so the client can retry with the same key.
                        try {
                            DYNAMO_CLIENT.deleteItem(software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest.builder()
                                    .tableName(IDEMPOTENCY_TABLE)
                                    .key(Map.of("idempotencyKey", AttributeValue.fromS(idempotencyKey)))
                                    .build());
                            logger.info("Stale FAILED idempotency record cleared; client may retry",
                                    Map.of("idempotencyKey", idempotencyKey));
                        } catch (Exception deleteEx) {
                            logger.error("Failed to delete FAILED idempotency record",
                                    Map.of("idempotencyKey", idempotencyKey,
                                           "error", String.valueOf(deleteEx.getMessage())));
                        }
                        return response(503, "{\"error\":\"Previous attempt failed, please retry with the same Idempotency-Key\"}");
                    }
                    // Record vanished (TTL race) — tell client to retry
                    return response(409, "{\"error\":\"A request with this Idempotency-Key is already in progress\"}");
                }
            }

            // --- Stock reservation via product catalog API ---
            // Reserve each item sequentially; on any failure, release already-reserved items
            // (compensating rollback) before returning an error.
            List<OrderItem> reserved = new ArrayList<>();
            for (OrderItem item : items) {
                int statusCode = callReserveStock(item.getProductId(), item.getQuantity(), logger);
                if (statusCode == 409) {
                    for (OrderItem r : reserved) {
                        callReleaseStock(r.getProductId(), r.getQuantity(), logger);
                    }
                    String errorBody = MAPPER.writeValueAsString(Map.of(
                            "error", "Insufficient stock",
                            "items", List.of(Map.of(
                                    "productId", item.getProductId(),
                                    "reason", "insufficient stock"))));
                    METRICS.count("StockInsufficient");
                    return response(409, errorBody);
                } else if (statusCode == 503) {
                    for (OrderItem r : reserved) {
                        callReleaseStock(r.getProductId(), r.getQuantity(), logger);
                    }
                    return response(503, "{\"error\":\"Product service unavailable, please retry\"}");
                } else if (statusCode != 200) {
                    for (OrderItem r : reserved) {
                        callReleaseStock(r.getProductId(), r.getQuantity(), logger);
                    }
                    logger.error("Unexpected response from product API", Map.of(
                            "productId", item.getProductId(), "statusCode", String.valueOf(statusCode)));
                    return response(502, "{\"error\":\"Failed to reserve stock\"}");
                }
                reserved.add(item);
            }

            // --- Save order ---
            double total = items.stream()
                    .mapToDouble(i -> i.getPrice() * i.getQuantity())
                    .sum();

            String orderId = UUID.randomUUID().toString();
            String itemsJson = MAPPER.writeValueAsString(items);

            Order order = new Order();
            order.setOrderId(orderId);
            order.setUserId(userId);
            order.setItemsJson(itemsJson);
            order.setTotalAmount(total);
            order.setStatus("PENDING");
            order.setCreatedAt(Instant.now().toString());

            // --- Publish event before persisting the order ---
            // SQS is published first so that if saveOrder fails, the event is
            // already in the queue and the payment Lambda retries until the order
            // record appears (or the message goes to the DLQ after 3 attempts).
            // Payment Lambda guards against processing a not-yet-saved order via
            // a conditional update on attribute_exists(orderId).
            OrderPlacedEvent event = new OrderPlacedEvent(orderId, userId, items, total);
            String eventJson = MAPPER.writeValueAsString(event);
            SQS_CLIENT.sendMessage(SendMessageRequest.builder()
                    .queueUrl(QUEUE_URL)
                    .messageBody(eventJson)
                    .build());

            // --- Persist the order ---
            REPOSITORY.saveOrder(order);

            logger.info("Order placed", Map.of("orderId", orderId, "userId", userId));
            METRICS.count("OrderPlaced");

            String responseBody = MAPPER.writeValueAsString(Map.of("orderId", orderId));

            // --- Mark idempotency record COMPLETED with the real response ---
            if (idempotencyKey != null && IDEMPOTENCY_TABLE != null) {
                DYNAMO_CLIENT.updateItem(UpdateItemRequest.builder()
                        .tableName(IDEMPOTENCY_TABLE)
                        .key(Map.of("idempotencyKey", AttributeValue.fromS(idempotencyKey)))
                        .updateExpression("SET #st = :completed, statusCode = :sc, responseBody = :rb, orderId = :oid")
                        .expressionAttributeNames(Map.of("#st", "status"))
                        .expressionAttributeValues(Map.of(
                                ":completed", AttributeValue.fromS("COMPLETED"),
                                ":sc", AttributeValue.fromN("201"),
                                ":rb", AttributeValue.fromS(responseBody),
                                ":oid", AttributeValue.fromS(orderId)
                        ))
                        .build());
            }

            return Map.of(
                    "statusCode", 201,
                    "headers", Map.of("Content-Type", "application/json"),
                    "body", responseBody
            );

        } catch (Exception e) {
            logger.error("Order placement failed", Map.of("error", String.valueOf(e.getMessage())));
            METRICS.count("OrderFailed");
            // Mark idempotency record FAILED so the client can retry with the same key.
            // Without this the slot stays IN_PROGRESS until the 24h TTL expires.
            if (idempotencyKey != null && IDEMPOTENCY_TABLE != null) {
                try {
                    DYNAMO_CLIENT.updateItem(UpdateItemRequest.builder()
                            .tableName(IDEMPOTENCY_TABLE)
                            .key(Map.of("idempotencyKey", AttributeValue.fromS(idempotencyKey)))
                            .updateExpression("SET #st = :failed")
                            .expressionAttributeNames(Map.of("#st", "status"))
                            .expressionAttributeValues(Map.of(
                                    ":failed", AttributeValue.fromS("FAILED")
                            ))
                            .build());
                    logger.info("Idempotency record marked FAILED",
                            Map.of("idempotencyKey", idempotencyKey));
                } catch (Exception idempotencyEx) {
                    logger.error("Failed to mark idempotency record FAILED",
                            Map.of("idempotencyKey", idempotencyKey,
                                   "error", String.valueOf(idempotencyEx.getMessage())));
                }
            }
            return response(500, "{\"error\":\"Failed to place order\"}");
        }
    }

    private int callReserveStock(String productId, int qty, JsonLogger logger) {
        try {
            String body = MAPPER.writeValueAsString(Map.of("reserve", qty));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(PRODUCTS_API_URL + "/products/" + productId + "/stock"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode();
        } catch (HttpTimeoutException e) {
            logger.error("Timeout calling reserve stock", Map.of("productId", productId));
            return 503;
        } catch (Exception e) {
            logger.error("Failed to call reserve stock", Map.of(
                    "productId", productId, "error", String.valueOf(e.getMessage())));
            return 500;
        }
    }

    private int callReleaseStock(String productId, int qty, JsonLogger logger) {
        try {
            String body = MAPPER.writeValueAsString(Map.of("release", qty));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(PRODUCTS_API_URL + "/products/" + productId + "/stock"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode();
        } catch (HttpTimeoutException e) {
            logger.error("Timeout calling release stock", Map.of("productId", productId));
            return 503;
        } catch (Exception e) {
            logger.error("Failed to call release stock", Map.of(
                    "productId", productId, "error", String.valueOf(e.getMessage())));
            return 500;
        }
    }

    private String extractHeader(Map<String, Object> headers, String name) {
        if (headers == null) return null;
        Object val = headers.get(name);
        if (val == null) val = headers.get(name.toLowerCase());
        return val != null ? val.toString() : null;
    }

    private Map<String, Object> response(int statusCode, String body) {
        return Map.of(
                "statusCode", statusCode,
                "headers", Map.of("Content-Type", "application/json"),
                "body", body
        );
    }
}

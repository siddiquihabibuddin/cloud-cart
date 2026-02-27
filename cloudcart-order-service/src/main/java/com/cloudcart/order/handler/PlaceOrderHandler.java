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
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;
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
    private static final String QUEUE_URL = System.getenv("ORDER_QUEUE_URL");
    private static final String PRODUCTS_TABLE = System.getenv("PRODUCTS_TABLE");
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
            String idempotencyKey = extractHeader(headers, "Idempotency-Key");
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
                        if ("COMPLETED".equals(stored.get("status").s())) {
                            int cachedStatus = Integer.parseInt(stored.get("statusCode").n());
                            String cachedBody = stored.get("responseBody").s();
                            logger.info("Idempotent response returned",
                                    Map.of("idempotencyKey", idempotencyKey));
                            return response(cachedStatus, cachedBody);
                        }
                    }
                    // IN_PROGRESS means a concurrent request is still running
                    return response(409, "{\"error\":\"A request with this Idempotency-Key is already in progress\"}");
                }
            }

            // --- Atomic stock reservation via TransactWriteItems (P1.1) ---
            List<TransactWriteItem> transactItems = new ArrayList<>();
            for (OrderItem item : items) {
                transactItems.add(TransactWriteItem.builder()
                        .update(Update.builder()
                                .tableName(PRODUCTS_TABLE)
                                .key(Map.of("productID", AttributeValue.fromS(item.getProductId())))
                                .updateExpression("SET stock = stock - :qty")
                                .conditionExpression("stock >= :qty")
                                .expressionAttributeValues(Map.of(
                                        ":qty", AttributeValue.fromN(String.valueOf(item.getQuantity()))
                                ))
                                .build())
                        .build());
            }

            try {
                DYNAMO_CLIENT.transactWriteItems(TransactWriteItemsRequest.builder()
                        .transactItems(transactItems)
                        .build());
            } catch (TransactionCanceledException e) {
                List<Map<String, String>> outOfStock = new ArrayList<>();
                List<CancellationReason> reasons = e.cancellationReasons();
                for (int i = 0; i < reasons.size() && i < items.size(); i++) {
                    if ("ConditionalCheckFailed".equals(reasons.get(i).code())) {
                        outOfStock.add(Map.of(
                                "productId", items.get(i).getProductId(),
                                "reason", "insufficient stock"
                        ));
                    }
                }
                String errorBody = MAPPER.writeValueAsString(Map.of(
                        "error", "Insufficient stock", "items", outOfStock));
                METRICS.count("StockInsufficient");
                return response(409, errorBody);
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
            return response(500, "{\"error\":\"Failed to place order\"}");
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

package com.cloudcart.payment.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.payment.model.OrderPlacedEvent;
import com.cloudcart.payment.model.PaymentSuccessEvent;
import com.cloudcart.payment.util.JsonLogger;
import com.cloudcart.payment.util.MetricsEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcessPaymentHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MetricsEmitter METRICS = new MetricsEmitter("CloudCart/Payments");
    private static final DynamoDbClient DYNAMO_CLIENT;
    private static final SqsClient SQS_CLIENT;
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String ORDERS_TABLE = System.getenv("ORDERS_TABLE");
    private static final String PAYMENT_SUCCESS_QUEUE_URL = System.getenv("PAYMENT_SUCCESS_QUEUE_URL");
    private static final String PRODUCTS_API_URL = System.getenv("PRODUCTS_API_URL");

    static {
        String endpointUrl = System.getenv("AWS_ENDPOINT_URL");
        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .retryPolicy(RetryPolicy.builder().numRetries(3).build())
                .build();

        DynamoDbClientBuilder dynamoBuilder = DynamoDbClient.builder().overrideConfiguration(overrideConfig);
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            dynamoBuilder.endpointOverride(URI.create(endpointUrl));
        }
        DYNAMO_CLIENT = dynamoBuilder.build();

        SqsClientBuilder sqsBuilder = SqsClient.builder().overrideConfiguration(overrideConfig);
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            sqsBuilder.endpointOverride(URI.create(endpointUrl));
        }
        SQS_CLIENT = sqsBuilder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        JsonLogger logger = new JsonLogger("payment-service", null);
        List<Map<String, String>> failedItems = new ArrayList<>();

        List<Map<String, Object>> records = (List<Map<String, Object>>) input.get("Records");
        if (records == null) {
            logger.info("No records in SQS event", null);
            return buildBatchResponse(failedItems);
        }

        for (Map<String, Object> record : records) {
            String messageId = (String) record.get("messageId");
            try {
                String body = (String) record.get("body");
                OrderPlacedEvent event = MAPPER.readValue(body, OrderPlacedEvent.class);

                String status = Math.random() < 0.8 ? "PAID" : "FAILED";

                // Condition: order must exist AND still be PENDING.
                // - attribute_not_exists / not-PENDING → ConditionalCheckFailedException
                //   → inspect order: if missing, add to failedItems so SQS retries
                //     (order-service publishes to SQS before saveOrder, so the record
                //     may not be written yet on a first attempt)
                //   → if present but already processed, skip silently (idempotent)
                try {
                    DYNAMO_CLIENT.updateItem(UpdateItemRequest.builder()
                            .tableName(ORDERS_TABLE)
                            .key(Map.of("orderId", AttributeValue.fromS(event.getOrderId())))
                            .updateExpression("SET #s = :status")
                            .conditionExpression("attribute_exists(orderId) AND #s = :pending")
                            .expressionAttributeNames(Map.of("#s", "status"))
                            .expressionAttributeValues(Map.of(
                                    ":status", AttributeValue.fromS(status),
                                    ":pending", AttributeValue.fromS("PENDING")
                            ))
                            .build());
                } catch (ConditionalCheckFailedException condEx) {
                    GetItemResponse check = DYNAMO_CLIENT.getItem(GetItemRequest.builder()
                            .tableName(ORDERS_TABLE)
                            .key(Map.of("orderId", AttributeValue.fromS(event.getOrderId())))
                            .build());
                    if (!check.hasItem() || check.item().isEmpty()) {
                        // Order not persisted yet — retry via SQS
                        logger.info("Order not found yet, deferring payment",
                                Map.of("orderId", event.getOrderId()));
                        if (messageId != null) failedItems.add(Map.of("itemIdentifier", messageId));
                    } else {
                        // Order exists but not PENDING — already processed, skip
                        logger.info("Payment already processed, skipping",
                                Map.of("orderId", event.getOrderId(),
                                       "currentStatus", check.item().get("status").s()));
                    }
                    continue;
                }

                logger.info("Payment processed", Map.of(
                        "orderId", event.getOrderId(),
                        "userId", event.getUserId(),
                        "total", String.valueOf(event.getTotalAmount()),
                        "status", status));

                if ("PAID".equals(status)) {
                    METRICS.count("PaymentSucceeded");
                    if (PAYMENT_SUCCESS_QUEUE_URL != null) {
                        try {
                            PaymentSuccessEvent successEvent = new PaymentSuccessEvent(
                                    event.getOrderId(), event.getUserId(),
                                    event.getItems(), event.getTotalAmount()
                            );
                            SQS_CLIENT.sendMessage(SendMessageRequest.builder()
                                    .queueUrl(PAYMENT_SUCCESS_QUEUE_URL)
                                    .messageBody(MAPPER.writeValueAsString(successEvent))
                                    .build());
                            logger.info("Published PaymentSuccessEvent",
                                    Map.of("orderId", event.getOrderId()));
                        } catch (Exception sqsEx) {
                            logger.error("Failed to publish PaymentSuccessEvent", Map.of(
                                    "orderId", event.getOrderId(),
                                    "error", String.valueOf(sqsEx.getMessage())));
                            if (messageId != null) {
                                failedItems.add(Map.of("itemIdentifier", messageId));
                            }
                        }
                    }
                } else {
                    METRICS.count("PaymentFailed");
                    // Release reserved stock so inventory is restored
                    if (PRODUCTS_API_URL != null && event.getItems() != null) {
                        for (com.cloudcart.payment.model.OrderItem item : event.getItems()) {
                            callReleaseStock(item.getProductId(), item.getQuantity(), logger);
                        }
                    }
                }

            } catch (Exception e) {
                logger.error("Error processing payment record", Map.of(
                        "messageId", messageId != null ? messageId : "unknown",
                        "error", String.valueOf(e.getMessage())));
                METRICS.count("PaymentError");
                if (messageId != null) {
                    failedItems.add(Map.of("itemIdentifier", messageId));
                }
            }
        }

        return buildBatchResponse(failedItems);
    }

    private void callReleaseStock(String productId, int qty, JsonLogger logger) {
        try {
            String body = MAPPER.writeValueAsString(Map.of("release", qty));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(PRODUCTS_API_URL + "/products/" + productId + "/stock"))
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            logger.error("Failed to release stock after payment failure", Map.of(
                    "productId", productId, "error", String.valueOf(e.getMessage())));
        }
    }

    private Map<String, Object> buildBatchResponse(List<Map<String, String>> failedItems) {
        Map<String, Object> response = new HashMap<>();
        response.put("batchItemFailures", failedItems);
        return response;
    }
}

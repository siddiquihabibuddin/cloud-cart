package com.cloudcart.order.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.order.model.OrderItem;
import com.cloudcart.order.model.StockCompensationEvent;
import com.cloudcart.order.repository.SagaRepository;
import com.cloudcart.order.util.JsonLogger;
import com.cloudcart.order.util.MetricsEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcessCompensationHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MetricsEmitter METRICS = new MetricsEmitter("CloudCart/Compensation");
    private static final SagaRepository SAGA_REPO;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final String PRODUCTS_API_URL = System.getenv("PRODUCTS_API_URL");

    static {
        String endpointUrl = System.getenv("AWS_ENDPOINT_URL");
        String sagaTable = System.getenv("SAGA_TABLE");

        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .retryPolicy(RetryPolicy.builder().numRetries(3).build())
                .build();

        DynamoDbClientBuilder dynamoBuilder = DynamoDbClient.builder().overrideConfiguration(overrideConfig);
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            dynamoBuilder.endpointOverride(URI.create(endpointUrl));
        }
        DynamoDbClient dynamoClient = dynamoBuilder.build();

        SAGA_REPO = new SagaRepository(dynamoClient, sagaTable);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        JsonLogger logger = new JsonLogger("compensation-service", null);
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
                StockCompensationEvent event = MAPPER.readValue(body, StockCompensationEvent.class);

                if (event.getItems() == null || event.getItems().isEmpty()) {
                    logger.info("No items to compensate", Map.of("orderId", event.getOrderId()));
                    continue;
                }

                boolean allReleased = true;
                for (OrderItem item : event.getItems()) {
                    int statusCode = callReleaseStock(item.getProductId(), item.getQuantity(), logger);
                    if (statusCode != 200) {
                        logger.error("Failed to release stock, will retry", Map.of(
                                "orderId", event.getOrderId(),
                                "productId", item.getProductId(),
                                "statusCode", String.valueOf(statusCode)));
                        if (messageId != null) failedItems.add(Map.of("itemIdentifier", messageId));
                        allReleased = false;
                        break;
                    }
                }

                if (allReleased) {
                    try {
                        SAGA_REPO.updateSagaStatus(
                                event.getOrderId(), "COMPENSATING", "COMPENSATION_COMPLETED", "STOCK_RELEASED");
                        METRICS.count("CompensationCompleted");
                        logger.info("Stock compensation completed", Map.of("orderId", event.getOrderId()));
                    } catch (Exception sagaEx) {
                        // Stock IS released — do NOT add to failedItems to avoid re-running HTTP calls
                        METRICS.count("SagaUpdateFailed");
                        logger.error("Failed to update saga after compensation", Map.of(
                                "orderId", event.getOrderId(),
                                "error", String.valueOf(sagaEx.getMessage())));
                    }
                }

            } catch (Exception e) {
                logger.error("Error processing compensation record", Map.of(
                        "messageId", messageId != null ? messageId : "unknown",
                        "error", String.valueOf(e.getMessage())));
                METRICS.count("CompensationError");
                if (messageId != null) {
                    failedItems.add(Map.of("itemIdentifier", messageId));
                }
            }
        }

        return buildBatchResponse(failedItems);
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
            logger.error("Timeout releasing stock", Map.of("productId", productId));
            return 503;
        } catch (Exception e) {
            logger.error("Failed to release stock", Map.of(
                    "productId", productId, "error", String.valueOf(e.getMessage())));
            return 500;
        }
    }

    private Map<String, Object> buildBatchResponse(List<Map<String, String>> failedItems) {
        Map<String, Object> response = new HashMap<>();
        response.put("batchItemFailures", failedItems);
        return response;
    }
}

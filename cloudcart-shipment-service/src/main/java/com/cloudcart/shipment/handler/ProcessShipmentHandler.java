package com.cloudcart.shipment.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.shipment.model.PaymentSuccessEvent;
import com.cloudcart.shipment.util.JsonLogger;
import com.cloudcart.shipment.util.MetricsEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProcessShipmentHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MetricsEmitter METRICS = new MetricsEmitter("CloudCart/Shipments");
    private static final DynamoDbClient DYNAMO_CLIENT;
    private static final String ORDERS_TABLE = System.getenv("ORDERS_TABLE");

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
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        JsonLogger logger = new JsonLogger("shipment-service", null);
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
                PaymentSuccessEvent event = MAPPER.readValue(body, PaymentSuccessEvent.class);

                String trackingId = "TRK-" + UUID.randomUUID().toString()
                        .replace("-", "").substring(0, 8).toUpperCase();
                String shippedAt = Instant.now().toString();

                // Condition: order must still be PAID.
                // - If already SHIPPED (duplicate delivery) → ConditionalCheckFailedException → skip silently (idempotent)
                // - If in any other state → also skip; this message shouldn't be here
                try {
                    DYNAMO_CLIENT.updateItem(UpdateItemRequest.builder()
                            .tableName(ORDERS_TABLE)
                            .key(Map.of("orderId", AttributeValue.fromS(event.getOrderId())))
                            .updateExpression("SET #s = :status, trackingId = :tid, shippedAt = :ts")
                            .conditionExpression("#s = :paid")
                            .expressionAttributeNames(Map.of("#s", "status"))
                            .expressionAttributeValues(Map.of(
                                    ":status", AttributeValue.fromS("SHIPPED"),
                                    ":tid", AttributeValue.fromS(trackingId),
                                    ":ts", AttributeValue.fromS(shippedAt),
                                    ":paid", AttributeValue.fromS("PAID")
                            ))
                            .build());
                } catch (ConditionalCheckFailedException condEx) {
                    logger.info("Shipment already processed or order not PAID, skipping",
                            Map.of("orderId", event.getOrderId()));
                    continue;
                }

                logger.info("Shipment initiated",
                        Map.of("orderId", event.getOrderId(), "trackingId", trackingId));
                METRICS.count("ShipmentInitiated");

            } catch (Exception e) {
                logger.error("Error processing shipment record", Map.of(
                        "messageId", messageId != null ? messageId : "unknown",
                        "error", String.valueOf(e.getMessage())));
                METRICS.count("ShipmentError");
                if (messageId != null) {
                    failedItems.add(Map.of("itemIdentifier", messageId));
                }
            }
        }

        return buildBatchResponse(failedItems);
    }

    private Map<String, Object> buildBatchResponse(List<Map<String, String>> failedItems) {
        Map<String, Object> response = new HashMap<>();
        response.put("batchItemFailures", failedItems);
        return response;
    }
}

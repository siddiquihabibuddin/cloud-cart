package com.cloudcart.shipment.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.shipment.model.OrderShippedEvent;
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
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProcessOrderShippedHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MetricsEmitter METRICS = new MetricsEmitter("CloudCart/Shipping");
    private static final DynamoDbClient DYNAMO_CLIENT;
    private static final SnsClient SNS_CLIENT;
    private static final String ORDERS_TABLE = System.getenv("ORDERS_TABLE");
    private static final String ORDER_SHIPPED_TOPIC_ARN = System.getenv("ORDER_SHIPPED_TOPIC_ARN");

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

        SnsClientBuilder snsBuilder = SnsClient.builder().overrideConfiguration(overrideConfig);
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            snsBuilder.endpointOverride(URI.create(endpointUrl));
        }
        SNS_CLIENT = snsBuilder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        JsonLogger logger = new JsonLogger("order-shipped-processor", null);
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
                OrderShippedEvent event = MAPPER.readValue(body, OrderShippedEvent.class);

                String trackingId = "TRK-" + UUID.randomUUID().toString().replace("-", "")
                        .substring(0, 8).toUpperCase();
                String shippedAt = Instant.now().toString();

                try {
                    DYNAMO_CLIENT.updateItem(UpdateItemRequest.builder()
                            .tableName(ORDERS_TABLE)
                            .key(Map.of("orderId", AttributeValue.fromS(event.getOrderId())))
                            .updateExpression("SET #s = :shipped, trackingId = :tid, shippedAt = :ts")
                            .conditionExpression("#s = :shipmentCreated")
                            .expressionAttributeNames(Map.of("#s", "status"))
                            .expressionAttributeValues(Map.of(
                                    ":shipped",         AttributeValue.fromS("SHIPPED"),
                                    ":tid",             AttributeValue.fromS(trackingId),
                                    ":ts",              AttributeValue.fromS(shippedAt),
                                    ":shipmentCreated", AttributeValue.fromS("SHIPMENT_CREATED")
                            ))
                            .build());
                } catch (ConditionalCheckFailedException condEx) {
                    logger.info("Order already shipped, skipping", Map.of("orderId", event.getOrderId()));
                    continue;
                }

                if (ORDER_SHIPPED_TOPIC_ARN != null) {
                    try {
                        Map<String, Object> notification = new HashMap<>();
                        notification.put("orderId", event.getOrderId());
                        notification.put("userId", event.getUserId());
                        notification.put("trackingId", trackingId);
                        SNS_CLIENT.publish(PublishRequest.builder()
                                .topicArn(ORDER_SHIPPED_TOPIC_ARN)
                                .subject("Your CloudCart order has shipped!")
                                .message(MAPPER.writeValueAsString(notification))
                                .build());
                        logger.info("SNS shipping notification sent", Map.of("orderId", event.getOrderId()));
                    } catch (Exception snsEx) {
                        // Non-fatal: order IS shipped. Log and continue.
                        logger.error("Failed to send SNS shipping notification", Map.of(
                                "orderId", event.getOrderId(),
                                "error", String.valueOf(snsEx.getMessage())));
                    }
                }

                logger.info("Order marked SHIPPED", Map.of(
                        "orderId", event.getOrderId(),
                        "trackingId", trackingId));
                METRICS.count("OrderShipped");

            } catch (Exception e) {
                logger.error("Error processing OrderShippedEvent", Map.of(
                        "messageId", messageId != null ? messageId : "unknown",
                        "error", String.valueOf(e.getMessage())));
                METRICS.count("OrderShippedError");
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

package com.cloudcart.shipment.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.shipment.util.JsonLogger;
import com.cloudcart.shipment.util.MetricsEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;
import java.util.Map;

public class ScanShipmentCreatedHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MetricsEmitter METRICS = new MetricsEmitter("CloudCart/Shipping");
    private static final DynamoDbClient DYNAMO_CLIENT;
    private static final SqsClient SQS_CLIENT;
    private static final String ORDERS_TABLE = System.getenv("ORDERS_TABLE");
    private static final String ORDER_SHIPPED_QUEUE_URL = System.getenv("ORDER_SHIPPED_QUEUE_URL");

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
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        JsonLogger logger = new JsonLogger("shipping-scanner", null);

        try {
            ScanResponse scanResponse = DYNAMO_CLIENT.scan(ScanRequest.builder()
                    .tableName(ORDERS_TABLE)
                    .filterExpression("#s = :status")
                    .expressionAttributeNames(Map.of("#s", "status"))
                    .expressionAttributeValues(Map.of(":status", AttributeValue.fromS("SHIPMENT_CREATED")))
                    .build());

            int count = 0;
            for (Map<String, AttributeValue> item : scanResponse.items()) {
                AttributeValue orderIdAttr = item.get("orderId");
                AttributeValue userIdAttr = item.get("userId");

                if (orderIdAttr == null || userIdAttr == null) {
                    logger.error("Skipping item with missing orderId or userId", null);
                    continue;
                }

                String orderId = orderIdAttr.s();
                String userId = userIdAttr.s();

                try {
                    String messageBody = MAPPER.writeValueAsString(Map.of(
                            "orderId", orderId,
                            "userId", userId
                    ));
                    SQS_CLIENT.sendMessage(SendMessageRequest.builder()
                            .queueUrl(ORDER_SHIPPED_QUEUE_URL)
                            .messageBody(messageBody)
                            .build());

                    METRICS.count("ShipmentScanPublished");
                    count++;
                } catch (Exception e) {
                    logger.error("Failed to publish OrderShippedEvent", Map.of(
                            "orderId", orderId,
                            "error", String.valueOf(e.getMessage())));
                }
            }

            logger.info("Scan complete", Map.of("published", count));
            return Map.of("published", count);

        } catch (Exception e) {
            logger.error("Fatal error during shipment scan", Map.of(
                    "error", String.valueOf(e.getMessage())));
            return Map.of("error", String.valueOf(e.getMessage()));
        }
    }
}

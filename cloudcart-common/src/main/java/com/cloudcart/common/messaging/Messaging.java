package com.cloudcart.common.messaging;

import com.cloudcart.common.log.Correlation;
import com.cloudcart.common.log.JsonLogger;
import com.cloudcart.common.log.Redactor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends SQS / SNS messages with the current {@code correlationId} attached as a message attribute
 * (which {@code LoggedHandler} reads on the consuming side, so a saga keeps one id across hops)
 * and logs an {@code sqs.out} / {@code sns.out} event with the redacted body.
 */
public final class Messaging {

    public static final String ATTRIBUTE = "correlationId";

    private Messaging() {}

    public static SendMessageResponse sendSqs(SqsClient client, String queueUrl, String body, JsonLogger log) {
        SendMessageResponse response = client.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .messageAttributes(Map.of(ATTRIBUTE, MessageAttributeValue.builder()
                        .dataType("String").stringValue(Correlation.current()).build()))
                .build());
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("queue", lastSegment(queueUrl));
        f.put("messageId", response.messageId());
        f.put("body", Redactor.body(body));
        log.event("sqs.out", "Sent message to " + lastSegment(queueUrl), f);
        return response;
    }

    public static PublishResponse publishSns(SnsClient client, String topicArn, String subject, String message, JsonLogger log) {
        PublishResponse response = client.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .subject(subject)
                .message(message)
                .messageAttributes(Map.of(ATTRIBUTE,
                        software.amazon.awssdk.services.sns.model.MessageAttributeValue.builder()
                                .dataType("String").stringValue(Correlation.current()).build()))
                .build());
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("topic", lastSegment(topicArn));
        f.put("subject", subject);
        f.put("messageId", response.messageId());
        f.put("body", Redactor.body(message));
        log.event("sns.out", "Published to " + lastSegment(topicArn), f);
        return response;
    }

    private static String lastSegment(String value) {
        if (value == null) return null;
        int slash = value.lastIndexOf('/');
        int colon = value.lastIndexOf(':');
        return value.substring(Math.max(slash, colon) + 1);
    }
}

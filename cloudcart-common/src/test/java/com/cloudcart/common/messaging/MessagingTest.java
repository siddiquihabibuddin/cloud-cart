package com.cloudcart.common.messaging;

import com.cloudcart.common.log.Correlation;
import com.cloudcart.common.log.JsonLogger;
import com.cloudcart.common.log.LogTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagingTest {

    private LogTestSupport logs;
    private final JsonLogger log = new JsonLogger("payment-service");

    @BeforeEach
    void setUp() {
        logs = LogTestSupport.capture();
    }

    @AfterEach
    void tearDown() {
        LogTestSupport.reset();
    }

    @Test
    void sqsMessagesCarryTheCorrelationIdAndAreLogged() {
        Correlation.set("saga-1");
        SqsClient sqs = mock(SqsClient.class);
        when(sqs.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().messageId("m-9").build());

        Messaging.sendSqs(sqs, "http://localhost:4566/000000000000/PaymentSuccessQueueDev",
                "{\"orderId\":\"o1\",\"password\":\"x\"}", log);

        ArgumentCaptor<SendMessageRequest> sent = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs).sendMessage(sent.capture());
        assertEquals("saga-1", sent.getValue().messageAttributes().get("correlationId").stringValue());
        assertEquals("{\"orderId\":\"o1\",\"password\":\"x\"}", sent.getValue().messageBody(), "the real message is unmodified");

        Map<String, Object> line = logs.event("sqs.out");
        assertEquals("PaymentSuccessQueueDev", line.get("queue"));
        assertEquals("m-9", line.get("messageId"));
        assertEquals("saga-1", line.get("correlationId"));
        assertTrue(line.get("body").toString().contains("\"password\":\"***\""));
        assertFalse(logs.anyLineContains("\"password\":\"x\""));
    }

    @Test
    void snsPublishesCarryTheCorrelationIdAndAreLogged() {
        Correlation.set("saga-2");
        SnsClient sns = mock(SnsClient.class);
        when(sns.publish(any(PublishRequest.class))).thenReturn(PublishResponse.builder().messageId("n-1").build());

        Messaging.publishSns(sns, "arn:aws:sns:us-east-1:000000000000:OrderShippedTopicDev", "Shipped", "{\"orderId\":\"o1\"}", log);

        ArgumentCaptor<PublishRequest> sent = ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(sent.capture());
        assertEquals("saga-2", sent.getValue().messageAttributes().get("correlationId").stringValue());

        Map<String, Object> line = logs.event("sns.out");
        assertEquals("OrderShippedTopicDev", line.get("topic"));
        assertEquals("Shipped", line.get("subject"));
        assertEquals("saga-2", line.get("correlationId"));
    }
}

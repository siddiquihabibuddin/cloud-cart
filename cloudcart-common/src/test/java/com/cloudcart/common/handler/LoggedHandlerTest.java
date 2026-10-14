package com.cloudcart.common.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.cloudcart.common.log.Correlation;
import com.cloudcart.common.log.LogTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggedHandlerTest {

    private LogTestSupport logs;

    @BeforeEach
    void setUp() {
        logs = LogTestSupport.capture();
    }

    @AfterEach
    void tearDown() {
        LogTestSupport.reset();
    }

    private static LoggedHandler handlerReturning(Map<String, Object> result) {
        return new LoggedHandler("test-service") {
            @Override
            protected Map<String, Object> process(Map<String, Object> input, Context context) {
                return result;
            }
        };
    }

    private static Map<String, Object> apiEvent(Map<String, Object> headers, String body) {
        return Map.of(
                "httpMethod", "POST",
                "path", "/auth/register",
                "resource", "/auth/register",
                "headers", headers,
                "requestContext", Map.of("requestId", "gw-req-1"),
                "body", body);
    }

    @Test
    void logsRedactedRequestAndResponseForApiGatewayEvents() {
        LoggedHandler handler = handlerReturning(Map.of("statusCode", 201, "body", "{\"token\":\"eyJabcdefgh.ijklmnop.qrstuv\"}"));

        Map<String, Object> out = handler.handleRequest(
                apiEvent(Map.of("Authorization", "Bearer abc.def", "X-Correlation-Id", "corr-77"),
                        "{\"email\":\"a@b.com\",\"password\":\"Hunter2!\"}"), null);

        assertEquals(201, out.get("statusCode"));
        Map<String, Object> in = logs.event("http.in");
        assertEquals("corr-77", in.get("correlationId"));
        assertEquals("test-service", in.get("service"));
        assertEquals("POST", in.get("method"));
        assertEquals("/auth/register", in.get("path"));
        assertTrue(in.get("body").toString().contains("\"password\":\"***\""));
        assertEquals("***", ((Map<?, ?>) in.get("headers")).get("Authorization"));

        Map<String, Object> res = logs.event("http.out");
        assertEquals("corr-77", res.get("correlationId"));
        assertEquals(201, res.get("status"));
        assertTrue(((Number) res.get("durationMs")).longValue() >= 0);
        assertTrue(res.get("body").toString().contains("\"token\":\"***\""));

        assertFalse(logs.anyLineContains("Hunter2"));
        assertFalse(logs.anyLineContains("eyJabcdefgh"));
        assertFalse(logs.anyLineContains("abc.def"));
    }

    @Test
    void fallsBackToTheApiGatewayRequestIdWhenNoHeader() {
        handlerReturning(Map.of("statusCode", 200, "body", "{}")).handleRequest(apiEvent(Map.of(), "{}"), null);
        assertEquals("gw-req-1", logs.event("http.in").get("correlationId"));
    }

    @Test
    void setsTheStaticCorrelationSoDeepHelpersSeeIt() {
        LoggedHandler handler = new LoggedHandler("svc") {
            @Override
            protected Map<String, Object> process(Map<String, Object> input, Context context) {
                return Map.of("statusCode", 200, "seen", Correlation.current());
            }
        };
        Map<String, Object> out = handler.handleRequest(apiEvent(Map.of("x-correlation-id", "abc"), "{}"), null);
        assertEquals("abc", out.get("seen"));
    }

    @Test
    void logsEachSqsRecordAndAdoptsTheCorrelationAttribute() {
        Map<String, Object> record = Map.of(
                "eventSource", "aws:sqs",
                "messageId", "m-1",
                "body", "{\"orderId\":\"o1\",\"password\":\"x\"}",
                "messageAttributes", Map.of("correlationId", Map.of("stringValue", "saga-9", "dataType", "String")));

        handlerReturning(Map.of()).handleRequest(Map.of("Records", List.of(record, record)), null);

        assertEquals("batch.in", logs.line(0).get("event"));
        assertEquals(2, ((Number) logs.line(0).get("recordCount")).intValue());
        Map<String, Object> sqs = logs.event("sqs.in");
        assertEquals("saga-9", sqs.get("correlationId"));
        assertEquals("m-1", sqs.get("messageId"));
        assertTrue(sqs.get("body").toString().contains("\"password\":\"***\""));
        assertEquals("saga-9", logs.event("invoke.out").get("correlationId"));
    }

    @Test
    void readsTheCorrelationIdFromAnSnsEnvelopeInTheSqsBody() {
        String envelope = "{\"Type\":\"Notification\",\"Message\":\"{}\","
                + "\"MessageAttributes\":{\"correlationId\":{\"Type\":\"String\",\"Value\":\"sns-5\"}}}";
        Map<String, Object> record = Map.of("eventSource", "aws:sqs", "messageId", "m", "body", envelope);

        handlerReturning(Map.of()).handleRequest(Map.of("Records", List.of(record)), null);

        assertEquals("sns-5", logs.event("sqs.in").get("correlationId"));
    }

    @Test
    void logsDynamoStreamRecordsByKeyOnly() {
        Map<String, Object> record = Map.of(
                "eventSource", "aws:dynamodb",
                "eventName", "INSERT",
                "dynamodb", Map.of("Keys", Map.of("productId", Map.of("S", "p1")),
                        "NewImage", Map.of("title", Map.of("S", "big payload"))));

        handlerReturning(Map.of()).handleRequest(Map.of("Records", List.of(record)), null);

        Map<String, Object> line = logs.event("stream.in");
        assertEquals("INSERT", line.get("eventName"));
        assertTrue(line.get("keys").toString().contains("p1"));
        assertFalse(logs.anyLineContains("big payload"));
    }

    @Test
    void logsOtherInvocationsGenerically() {
        handlerReturning(Map.of("ok", true)).handleRequest(Map.of("source", "scheduler"), null);
        assertEquals("invoke.in", logs.line(0).get("event"));
        assertEquals("invoke.out", logs.line(1).get("event"));
    }

    @Test
    void logsAndRethrowsUnhandledExceptionsSoRetriesStillHappen() {
        LoggedHandler handler = new LoggedHandler("svc") {
            @Override
            protected Map<String, Object> process(Map<String, Object> input, Context context) {
                throw new IllegalStateException("db down");
            }
        };

        assertThrows(IllegalStateException.class, () -> handler.handleRequest(apiEvent(Map.of(), "{}"), null));

        Map<String, Object> res = logs.event("http.out");
        assertEquals("ERROR", res.get("level"));
        assertEquals(500, res.get("status"));
        assertEquals("java.lang.IllegalStateException", res.get("errorType"));
        assertEquals("db down", res.get("errorMessage"));
    }

    @Test
    void derivesTheServiceNameFromThePackage() {
        // com.cloudcart.<name>.handler.X -> "<name>-service"
        assertEquals("common-service", LoggedHandler.defaultServiceName(LoggedHandlerTest.class));
        // anything outside com.cloudcart falls back to the simple class name
        assertEquals("String", LoggedHandler.defaultServiceName(String.class));
    }
}

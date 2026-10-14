package com.cloudcart.common.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonLoggerTest {

    private LogTestSupport logs;

    @BeforeEach
    void setUp() {
        logs = LogTestSupport.capture();
    }

    @AfterEach
    void tearDown() {
        LogTestSupport.reset();
    }

    @Test
    void emitsTheStandardFieldsAsOneJsonLine() {
        Correlation.set("corr-1");
        new JsonLogger("order-service").info("hello", Map.of("orderId", "o1"));

        assertEquals(1, logs.lines().size());
        Map<String, Object> line = logs.line(0);
        assertEquals("INFO", line.get("level"));
        assertEquals("order-service", line.get("service"));
        assertEquals("corr-1", line.get("correlationId"));
        assertEquals("hello", line.get("message"));
        assertEquals("o1", line.get("orderId"));
        assertTrue(line.containsKey("timestamp"));
    }

    @Test
    void followsTheCurrentCorrelationIdAtLogTime() {
        JsonLogger log = new JsonLogger("svc");
        Correlation.set("first");
        log.info("a", null);
        Correlation.set("second");
        log.info("b", null);

        assertEquals("first", logs.line(0).get("correlationId"));
        assertEquals("second", logs.line(1).get("correlationId"));
    }

    @Test
    void aPinnedCorrelationIdWins() {
        Correlation.set("current");
        new JsonLogger("svc", "pinned").info("a", null);
        assertEquals("pinned", logs.line(0).get("correlationId"));
    }

    @Test
    void respectsLogLevel() {
        LogConfig.setEnv(k -> "LOG_LEVEL".equals(k) ? "ERROR" : null);
        JsonLogger log = new JsonLogger("svc");
        log.info("hidden", null);
        log.warn("hidden too", null);
        log.error("shown", null);

        assertEquals(1, logs.lines().size());
        assertEquals("shown", logs.line(0).get("message"));
    }

    @Test
    void errorWithThrowableAddsTypeMessageAndStack() {
        new JsonLogger("svc").error("boom", null, new IllegalStateException("bad Bearer secret.tok"));

        Map<String, Object> line = logs.line(0);
        assertEquals("java.lang.IllegalStateException", line.get("errorType"));
        assertEquals("bad Bearer ***", line.get("errorMessage"));
        assertTrue(line.get("stack").toString().contains("IllegalStateException"));
    }

    @Test
    void nullExtraValuesAreOmitted() {
        Map<String, Object> extra = new HashMap<>();
        extra.put("present", "x");
        extra.put("absent", null);
        new JsonLogger("svc").info("m", extra);

        assertTrue(logs.line(0).containsKey("present"));
        assertFalse(logs.line(0).containsKey("absent"));
    }

    @Test
    void eventsCarryTheirName() {
        new JsonLogger("svc").event("llm.request", "calling", Map.of("model", "m"));
        assertEquals("llm.request", logs.line(0).get("event"));
    }
}

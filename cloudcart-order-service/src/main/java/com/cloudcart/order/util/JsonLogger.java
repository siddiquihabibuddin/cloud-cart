package com.cloudcart.order.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class JsonLogger {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String service;
    private final String correlationId;

    public JsonLogger(String service, String correlationId) {
        this.service = service;
        this.correlationId = (correlationId != null && !correlationId.isBlank())
                ? correlationId : UUID.randomUUID().toString();
    }

    public static JsonLogger fromHeaders(String service, Map<String, Object> headers) {
        String corrId = null;
        if (headers != null) {
            Object h = headers.get("X-Correlation-Id");
            if (h == null) h = headers.get("x-correlation-id");
            if (h != null) corrId = h.toString();
        }
        return new JsonLogger(service, corrId);
    }

    public void info(String message, Map<String, Object> extra) {
        log("INFO", message, extra);
    }

    public void error(String message, Map<String, Object> extra) {
        log("ERROR", message, extra);
    }

    public String getCorrelationId() {
        return correlationId;
    }

    private void log(String level, String message, Map<String, Object> extra) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", Instant.now().toString());
        entry.put("level", level);
        entry.put("service", service);
        entry.put("correlationId", correlationId);
        entry.put("message", message);
        if (extra != null) entry.putAll(extra);
        try {
            System.out.println(MAPPER.writeValueAsString(entry));
        } catch (Exception e) {
            System.out.println("{\"level\":\"ERROR\",\"message\":\"Failed to serialize log entry\"}");
        }
    }
}

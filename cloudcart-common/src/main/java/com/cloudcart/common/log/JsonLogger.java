package com.cloudcart.common.log;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One JSON object per line on stdout. Fields: timestamp, level, service, correlationId,
 * [event], message, plus any extra fields. This is the single logger for all CloudCart Lambdas;
 * the field layout matches the per-service copies it replaces.
 *
 * <p>Created with just a service name, the correlation id is read from {@link Correlation} at
 * log time, so it follows the request currently being handled.
 */
public class JsonLogger {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_STACK_CHARS = 4000;

    private static volatile Consumer<String> sink = System.out::println;

    private final String service;
    private final String fixedCorrelationId;

    public JsonLogger(String service) {
        this(service, null);
    }

    /** Pins the logger to a specific correlation id instead of following {@link Correlation}. */
    public JsonLogger(String service, String correlationId) {
        this.service = service;
        this.fixedCorrelationId = (correlationId != null && !correlationId.isBlank()) ? correlationId : null;
    }

    /** Uses the X-Correlation-Id header when present, otherwise the current request's id. */
    public static JsonLogger fromHeaders(String service, Map<String, Object> headers) {
        String id = Correlation.fromHeaders(headers);
        if (id != null) Correlation.set(id);
        return new JsonLogger(service);
    }

    /** Test hook: capture log lines. Pass null to restore stdout. */
    public static void setSink(Consumer<String> newSink) {
        sink = newSink == null ? System.out::println : newSink;
    }

    public String getCorrelationId() {
        return fixedCorrelationId != null ? fixedCorrelationId : Correlation.current();
    }

    public void debug(String message, Map<String, Object> extra) { log(LogConfig.Level.DEBUG, null, message, extra, null); }
    public void info(String message, Map<String, Object> extra) { log(LogConfig.Level.INFO, null, message, extra, null); }
    public void warn(String message, Map<String, Object> extra) { log(LogConfig.Level.WARN, null, message, extra, null); }
    public void error(String message, Map<String, Object> extra) { log(LogConfig.Level.ERROR, null, message, extra, null); }

    public void error(String message, Map<String, Object> extra, Throwable t) {
        log(LogConfig.Level.ERROR, null, message, extra, t);
    }

    /** Structured event (http.in, llm.response, ...): INFO unless a level is given. */
    public void event(String event, String message, Map<String, Object> extra) {
        log(LogConfig.Level.INFO, event, message, extra, null);
    }

    public void event(LogConfig.Level level, String event, String message, Map<String, Object> extra) {
        log(level, event, message, extra, null);
    }

    private void log(LogConfig.Level level, String event, String message, Map<String, Object> extra, Throwable t) {
        if (level.ordinal() < LogConfig.level().ordinal()) return;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", Instant.now().toString());
        entry.put("level", level.name());
        entry.put("service", service);
        entry.put("correlationId", getCorrelationId());
        if (event != null) entry.put("event", event);
        entry.put("message", message);
        if (extra != null) {
            extra.forEach((k, v) -> {
                if (v != null) entry.put(k, v);
            });
        }
        if (t != null) {
            entry.put("errorType", t.getClass().getName());
            entry.put("errorMessage", Redactor.sanitizeText(String.valueOf(t.getMessage())));
            entry.put("stack", Redactor.truncate(stackTrace(t), MAX_STACK_CHARS));
        }
        try {
            sink.accept(MAPPER.writeValueAsString(entry));
        } catch (Exception e) {
            sink.accept("{\"level\":\"ERROR\",\"message\":\"Failed to serialize log entry\"}");
        }
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}

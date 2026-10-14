package com.cloudcart.common.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.common.log.Correlation;
import com.cloudcart.common.log.JsonLogger;
import com.cloudcart.common.log.LogConfig;
import com.cloudcart.common.log.Redactor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for every CloudCart Lambda. {@link #handleRequest} is final: it establishes the
 * correlation id, logs the inbound event, calls {@link #process}, logs the outcome and its
 * duration, and rethrows unhandled exceptions after logging them (so SQS / stream retry and
 * DLQ behaviour is unchanged).
 *
 * <p>Subclasses implement {@link #process} with the same body their old {@code handleRequest} had.
 * Three kinds of events are recognised:
 * <ul>
 *   <li>API Gateway proxy requests ({@code httpMethod}) - logged as {@code http.in} / {@code http.out}</li>
 *   <li>SQS / DynamoDB stream batches ({@code Records}) - one {@code sqs.in} / {@code stream.in} per record</li>
 *   <li>anything else (Step Functions, schedulers) - {@code invoke.in} / {@code invoke.out}</li>
 * </ul>
 */
public abstract class LoggedHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JsonLogger log;

    protected LoggedHandler() {
        this(null);
    }

    protected LoggedHandler(String serviceName) {
        this.log = new JsonLogger(serviceName != null ? serviceName : defaultServiceName(getClass()));
    }

    /** The handler's real work; same contract as the old {@code handleRequest}. */
    protected abstract Map<String, Object> process(Map<String, Object> input, Context context);

    /** The shared logger (follows the current request's correlation id). */
    protected JsonLogger logger() {
        return log;
    }

    @Override
    public final Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        long start = System.nanoTime();
        Kind kind = kindOf(input);
        Correlation.set(correlationIdFor(input, kind));
        String awsRequestId = context != null ? context.getAwsRequestId() : null;

        logInbound(input, kind, awsRequestId);
        try {
            Map<String, Object> output = process(input, context);
            logOutbound(output, kind, start, null);
            return output;
        } catch (RuntimeException | Error e) {
            logOutbound(null, kind, start, e);
            throw e;
        }
    }

    // ---- classification -------------------------------------------------------------------

    enum Kind { HTTP, RECORDS, OTHER }

    static Kind kindOf(Map<String, Object> input) {
        if (input == null) return Kind.OTHER;
        if (input.containsKey("httpMethod")) return Kind.HTTP;
        if (input.get("Records") instanceof List<?>) return Kind.RECORDS;
        return Kind.OTHER;
    }

    static String correlationIdFor(Map<String, Object> input, Kind kind) {
        if (input == null) return null;
        if (kind == Kind.HTTP) {
            String fromHeader = Correlation.fromHeaders(asMap(input.get("headers")));
            if (fromHeader != null) return fromHeader;
            Object rc = asMap(input.get("requestContext")).get("requestId");
            return rc != null ? rc.toString() : null;
        }
        if (kind == Kind.RECORDS) {
            for (Map<String, Object> record : records(input)) {
                String id = recordCorrelationId(record);
                if (id != null) return id;
            }
        }
        return null;
    }

    /** SQS attribute first, then an SNS envelope's MessageAttributes inside the body. */
    @SuppressWarnings("unchecked")
    static String recordCorrelationId(Map<String, Object> record) {
        Object attr = asMap(record.get("messageAttributes")).get("correlationId");
        if (attr instanceof Map<?, ?> m && m.get("stringValue") != null) {
            return m.get("stringValue").toString();
        }
        Object body = record.get("body");
        if (body instanceof String s && s.contains("MessageAttributes")) {
            try {
                Map<String, Object> envelope = MAPPER.readValue(s, Map.class);
                Object a = asMap(envelope.get("MessageAttributes")).get("correlationId");
                if (a instanceof Map<?, ?> m && m.get("Value") != null) return m.get("Value").toString();
            } catch (Exception ignored) {
                // not an SNS envelope
            }
        }
        return null;
    }

    // ---- logging --------------------------------------------------------------------------

    private void logInbound(Map<String, Object> input, Kind kind, String awsRequestId) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("awsRequestId", awsRequestId);
        switch (kind) {
            case HTTP -> {
                f.put("method", input.get("httpMethod"));
                f.put("path", input.get("path") != null ? input.get("path") : input.get("resource"));
                f.put("resource", input.get("resource"));
                f.put("query", input.get("queryStringParameters"));
                f.put("headers", Redactor.headers(asMap(input.get("headers"))));
                f.put("body", Redactor.body((Object) input.get("body")));
                log.event("http.in", "Request received", f);
            }
            case RECORDS -> {
                List<Map<String, Object>> records = records(input);
                f.put("recordCount", records.size());
                log.event("batch.in", "Batch received", f);
                for (Map<String, Object> record : records) {
                    logRecord(record);
                }
            }
            default -> {
                f.put("body", Redactor.body(input));
                log.event("invoke.in", "Invocation received", f);
            }
        }
    }

    private void logRecord(Map<String, Object> record) {
        Map<String, Object> f = new LinkedHashMap<>();
        String source = String.valueOf(record.get("eventSource"));
        boolean sqs = "aws:sqs".equals(source);
        String recordId = recordCorrelationId(record);
        if (recordId != null) f.put("correlationId", recordId);
        f.put("source", source);
        if (sqs) {
            f.put("messageId", record.get("messageId"));
            f.put("body", Redactor.body((Object) record.get("body")));
            log.event("sqs.in", "Message received", f);
        } else {
            Map<String, Object> ddb = asMap(record.get("dynamodb"));
            f.put("eventName", record.get("eventName"));
            f.put("keys", Redactor.body((Object) ddb.get("Keys")));
            log.event("stream.in", "Stream record received", f);
        }
    }

    private void logOutbound(Map<String, Object> output, Kind kind, long startNanos, Throwable error) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("durationMs", (System.nanoTime() - startNanos) / 1_000_000);
        String event = kind == Kind.HTTP ? "http.out" : "invoke.out";
        if (error != null) {
            f.put("status", kind == Kind.HTTP ? 500 : null);
            log.event(LogConfig.Level.ERROR, event, "Unhandled exception", withError(f, error));
            return;
        }
        if (output != null) {
            if (kind == Kind.HTTP) {
                f.put("status", output.get("statusCode"));
                f.put("body", Redactor.body((Object) output.get("body")));
            } else {
                f.put("body", Redactor.body(output));
            }
        }
        log.event(event, "Request completed", f);
    }

    private Map<String, Object> withError(Map<String, Object> fields, Throwable t) {
        fields.put("errorType", t.getClass().getName());
        fields.put("errorMessage", Redactor.sanitizeText(String.valueOf(t.getMessage())));
        return fields;
    }

    // ---- helpers --------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> records(Map<String, Object> input) {
        Object r = input.get("Records");
        return r instanceof List<?> ? (List<Map<String, Object>>) r : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> ? (Map<String, Object>) o : Map.of();
    }

    /** com.cloudcart.order.handler.X -> "order-service"; SERVICE_NAME overrides. */
    static String defaultServiceName(Class<?> type) {
        String env = System.getenv("SERVICE_NAME");
        if (env != null && !env.isBlank()) return env;
        String[] parts = type.getPackageName().split("\\.");
        return parts.length > 2 && "cloudcart".equals(parts[1]) ? parts[2] + "-service" : type.getSimpleName();
    }
}

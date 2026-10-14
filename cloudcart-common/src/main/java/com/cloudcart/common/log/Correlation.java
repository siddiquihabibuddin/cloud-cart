package com.cloudcart.common.log;

import java.util.Map;
import java.util.UUID;

/**
 * Holds the correlation id of the request currently being handled.
 *
 * <p>A Lambda container serves one invocation at a time, so a plain static is safe and lets
 * deep helpers (outbound HTTP, SQS sends, the MCP transport) pick the id up without it being
 * threaded through every method signature.
 */
public final class Correlation {

    public static final String HEADER = "X-Correlation-Id";

    private static volatile String current;

    private Correlation() {}

    public static String current() {
        String id = current;
        return id != null ? id : set(null);
    }

    /** Sets the id (generating one when blank) and returns it. */
    public static String set(String id) {
        String value = (id != null && !id.isBlank()) ? id.trim() : UUID.randomUUID().toString();
        current = value;
        return value;
    }

    public static void clear() {
        current = null;
    }

    /** Case-insensitive lookup of X-Correlation-Id in an HTTP header map; null when absent. */
    public static String fromHeaders(Map<String, ?> headers) {
        if (headers == null) return null;
        for (Map.Entry<String, ?> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(HEADER) && e.getValue() != null) {
                String v = e.getValue().toString();
                if (!v.isBlank()) return v;
            }
        }
        return null;
    }
}

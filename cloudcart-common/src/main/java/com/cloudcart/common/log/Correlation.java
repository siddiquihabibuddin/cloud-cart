package com.cloudcart.common.log;

import java.util.Map;
import java.util.UUID;

/**
 * Holds the correlation id of the request currently being handled.
 *
 * <p>Two layers. The thread-local is authoritative for the thread that set it (and threads it
 * spawns), which is what a multi-threaded server such as the MCP server needs. The static
 * {@code last} value is a fallback for threads that never set one: a Lambda container serves one
 * invocation at a time, so the last id set <em>is</em> the current request, and that is how the
 * MCP client's internal threads pick it up.
 */
public final class Correlation {

    public static final String HEADER = "X-Correlation-Id";

    private static final InheritableThreadLocal<String> LOCAL = new InheritableThreadLocal<>();
    private static volatile String last;

    private Correlation() {}

    public static String current() {
        String id = LOCAL.get();
        if (id != null) return id;
        id = last;
        return id != null ? id : set(null);
    }

    /** Sets the id (generating one when blank) for this thread and returns it. */
    public static String set(String id) {
        String value = (id != null && !id.isBlank()) ? id.trim() : UUID.randomUUID().toString();
        LOCAL.set(value);
        last = value;
        return value;
    }

    /** Forgets this thread's id only; other threads and the fallback are untouched. */
    public static void clearThread() {
        LOCAL.remove();
    }

    public static void clear() {
        LOCAL.remove();
        last = null;
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

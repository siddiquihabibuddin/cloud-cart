package com.cloudcart.common.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Masks secrets in anything that is about to be logged, then truncates it.
 *
 * <p>Two layers: (1) JSON keys / HTTP header names that hold secrets have their value replaced,
 * matched on the normalized name so {@code authToken} and {@code x-api-key} are caught but Groq's
 * {@code totalTokens} usage counters are not; (2) value patterns (bearer tokens, JWTs, Groq keys)
 * are masked wherever they occur, including inside free text such as chat messages.
 */
public final class Redactor {

    public static final String MASK = "***";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Normalized (lower-case, letters/digits only) names whose values are always masked. */
    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "password", "passwd", "pwd", "secret", "clientsecret",
            "token", "accesstoken", "refreshtoken", "idtoken", "authtoken",
            "authorization", "proxyauthorization",
            "apikey", "xapikey", "groqapikey", "jwtsecret",
            "cookie", "setcookie", "jwt");

    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");
    private static final Pattern GROQ_KEY = Pattern.compile("gsk_[A-Za-z0-9]{10,}");

    private Redactor() {}

    static boolean isSensitiveName(String name) {
        if (name == null) return false;
        String n = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        return SENSITIVE_NAMES.contains(n) || n.endsWith("password") || n.endsWith("secret") || n.endsWith("apikey");
    }

    /** Masks bearer tokens, JWTs and Groq keys inside free text. */
    public static String sanitizeText(String text) {
        if (text == null) return null;
        String out = BEARER.matcher(text).replaceAll("Bearer " + MASK);
        out = JWT.matcher(out).replaceAll(MASK);
        out = GROQ_KEY.matcher(out).replaceAll(MASK);
        return out;
    }

    /**
     * Redacts and truncates a request/response body for logging. Returns null when
     * {@code LOG_BODIES=false}, so callers can simply skip the field.
     */
    public static String body(String raw) {
        if (raw == null || !LogConfig.logBodies()) return null;
        return truncate(redactJsonOrText(raw), LogConfig.maxBodyChars());
    }

    /** Same as {@link #body(String)} for an object (e.g. tool arguments), serialized as JSON. */
    public static String body(Object value) {
        if (value == null || !LogConfig.logBodies()) return null;
        if (value instanceof CharSequence cs) return body(cs.toString());
        try {
            return body(MAPPER.writeValueAsString(value));
        } catch (Exception e) {
            return truncate(sanitizeText(String.valueOf(value)), LogConfig.maxBodyChars());
        }
    }

    /** Headers with secret-bearing names masked and value patterns scrubbed. Never null. */
    public static Map<String, Object> headers(Map<String, ?> headers) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (headers == null) return out;
        for (Map.Entry<String, ?> e : headers.entrySet()) {
            Object v = e.getValue();
            if (isSensitiveName(e.getKey())) {
                out.put(e.getKey(), MASK);
            } else {
                out.put(e.getKey(), v instanceof CharSequence ? sanitizeText(v.toString()) : v);
            }
        }
        return out;
    }

    public static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "…[truncated " + (s.length() - max) + " chars]";
    }

    private static String redactJsonOrText(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode tree = MAPPER.readTree(trimmed);
                return MAPPER.writeValueAsString(redactNode(tree));
            } catch (Exception ignored) {
                // not valid JSON (or truncated upstream) - fall through to text scrubbing
            }
        }
        return sanitizeText(raw);
    }

    private static JsonNode redactNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> field = it.next();
                if (isSensitiveName(field.getKey()) && !field.getValue().isContainerNode()) {
                    field.setValue(MAPPER.getNodeFactory().textNode(MASK));
                } else {
                    field.setValue(redactNode(field.getValue()));
                }
            }
            return obj;
        }
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, redactNode(arr.get(i)));
            }
            return arr;
        }
        if (node.isTextual()) {
            return MAPPER.getNodeFactory().textNode(sanitizeText(node.asText()));
        }
        return node;
    }
}

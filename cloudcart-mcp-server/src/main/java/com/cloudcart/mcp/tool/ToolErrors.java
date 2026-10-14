package com.cloudcart.mcp.tool;

import com.cloudcart.common.log.JsonLogger;
import com.cloudcart.common.log.Redactor;

import java.util.Map;

final class ToolErrors {

    private static final JsonLogger LOG = new JsonLogger("mcp-server");

    private ToolErrors() {}

    /** Tools report failures to the model as {"error": ...}; the exception itself is logged here. */
    static Map<String, Object> from(Exception e) {
        LOG.error("Tool call failed", Map.of("errorMessage", Redactor.sanitizeText(String.valueOf(e.getMessage()))), e);
        return Map.of("error", String.valueOf(e.getMessage()));
    }
}

package com.cloudcart.mcp.tool;

import java.util.Map;

final class ToolErrors {

    private ToolErrors() {}

    static Map<String, Object> from(Exception e) {
        return Map.of("error", String.valueOf(e.getMessage()));
    }
}

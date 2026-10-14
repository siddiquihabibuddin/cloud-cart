package com.cloudcart.mcp.config;

import com.cloudcart.common.log.JsonLogger;
import com.cloudcart.common.log.LogConfig;
import com.cloudcart.common.log.Redactor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decorates a tool callback with request/response logging: one {@code tool.call} event carrying the
 * tool name, redacted arguments (the agent passes the caller's JWT as {@code authToken}), the
 * redacted result and the duration. Failures are logged and rethrown unchanged.
 */
public class LoggingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final JsonLogger log = new JsonLogger("mcp-server");

    public LoggingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return logged(toolInput, () -> delegate.call(toolInput));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return logged(toolInput, () -> delegate.call(toolInput, toolContext));
    }

    private String logged(String toolInput, java.util.function.Supplier<String> invocation) {
        String name = delegate.getToolDefinition().name();
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("tool", name);
        f.put("arguments", Redactor.body(toolInput));
        long start = System.nanoTime();
        try {
            String result = invocation.get();
            f.put("durationMs", (System.nanoTime() - start) / 1_000_000);
            f.put("result", Redactor.body(result));
            // tools report failures as {"error": "..."} rather than throwing
            boolean failed = result != null && result.startsWith("{\"error\"");
            f.put("failed", failed);
            log.event(failed ? LogConfig.Level.WARN : LogConfig.Level.INFO, "tool.call",
                    "Tool " + name + (failed ? " returned an error" : " completed"), f);
            return result;
        } catch (RuntimeException e) {
            f.put("durationMs", (System.nanoTime() - start) / 1_000_000);
            f.put("failed", true);
            f.put("errorType", e.getClass().getName());
            f.put("errorMessage", Redactor.sanitizeText(String.valueOf(e.getMessage())));
            log.event(LogConfig.Level.ERROR, "tool.call", "Tool " + name + " threw", f);
            throw e;
        }
    }
}

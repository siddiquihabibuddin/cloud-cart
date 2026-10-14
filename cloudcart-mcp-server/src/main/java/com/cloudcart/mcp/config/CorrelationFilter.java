package com.cloudcart.mcp.config;

import com.cloudcart.common.log.Correlation;
import com.cloudcart.common.log.JsonLogger;
import com.cloudcart.common.log.LogConfig;
import com.cloudcart.common.log.Redactor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Binds the caller's {@code X-Correlation-Id} for the duration of each request so every log line
 * and outbound call made while serving it carries the id the agent Lambda started with, and logs
 * the inbound MCP call.
 *
 * <p>The MCP response may be streamed (SSE), so its body is deliberately not captured here: the
 * tool result is logged by {@link LoggingToolCallback}. This filter logs the JSON-RPC request
 * (after it has been consumed), status and duration.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {

    private static final int CACHE_LIMIT = 64 * 1024;

    private final JsonLogger log = new JsonLogger("mcp-server");

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = Correlation.set(request.getHeader(Correlation.HEADER));
        response.setHeader(Correlation.HEADER, id);
        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request, CACHE_LIMIT);
        long start = System.nanoTime();
        try {
            chain.doFilter(wrapped, response);
        } finally {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("method", request.getMethod());
            f.put("path", request.getRequestURI());
            f.put("status", response.getStatus());
            f.put("durationMs", (System.nanoTime() - start) / 1_000_000);
            if (LogConfig.logBodies() && wrapped.getContentAsByteArray().length > 0) {
                f.put("body", Redactor.body(new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8)));
            }
            log.event(response.getStatus() >= 500 ? LogConfig.Level.ERROR : LogConfig.Level.INFO,
                    "http.in", request.getMethod() + " " + request.getRequestURI(), f);
            Correlation.clearThread();
        }
    }
}

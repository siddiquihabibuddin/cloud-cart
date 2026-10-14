package com.cloudcart.mcp.config;

import com.cloudcart.common.log.Correlation;
import com.cloudcart.common.log.JsonLogger;
import com.cloudcart.common.log.LogConfig;
import com.cloudcart.common.log.Redactor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Logs every outbound call to the unified API gateway (headers and bodies redacted) and forwards
 * the current {@code X-Correlation-Id} so the Lambdas behind the gateway log under the same id.
 */
public class LoggingRequestInterceptor implements ClientHttpRequestInterceptor {

    private final JsonLogger log = new JsonLogger("mcp-server");
    private final String target;

    public LoggingRequestInterceptor(String target) {
        this.target = target;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().set(Correlation.HEADER, Correlation.current());

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("target", target);
        req.put("method", request.getMethod().name());
        req.put("url", Redactor.sanitizeText(request.getURI().toString()));
        req.put("headers", Redactor.headers(request.getHeaders().toSingleValueMap()));
        if (body != null && body.length > 0) {
            req.put("body", Redactor.body(new String(body, StandardCharsets.UTF_8)));
        }
        log.event("client.request", "Calling " + target, req);

        long start = System.nanoTime();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("target", target);
            res.put("method", request.getMethod().name());
            res.put("url", Redactor.sanitizeText(request.getURI().toString()));
            int status = response.getStatusCode().value();
            res.put("status", status);
            res.put("durationMs", (System.nanoTime() - start) / 1_000_000);
            if (LogConfig.logBodies()) {
                res.put("body", Redactor.body(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
            }
            log.event(status >= 500 ? LogConfig.Level.ERROR : status >= 400 ? LogConfig.Level.WARN : LogConfig.Level.INFO,
                    "client.response", target + " responded " + status, res);
            return response;
        } catch (IOException | RuntimeException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("target", target);
            err.put("method", request.getMethod().name());
            err.put("url", Redactor.sanitizeText(request.getURI().toString()));
            err.put("durationMs", (System.nanoTime() - start) / 1_000_000);
            err.put("errorType", e.getClass().getName());
            err.put("errorMessage", Redactor.sanitizeText(String.valueOf(e.getMessage())));
            log.event(LogConfig.Level.ERROR, "client.response", target + " call failed", err);
            throw e;
        }
    }
}

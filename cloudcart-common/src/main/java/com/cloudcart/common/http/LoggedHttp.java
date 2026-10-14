package com.cloudcart.common.http;

import com.cloudcart.common.log.Correlation;
import com.cloudcart.common.log.JsonLogger;
import com.cloudcart.common.log.LogConfig;
import com.cloudcart.common.log.Redactor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wraps {@link HttpClient#send} for outbound calls: adds the current {@code X-Correlation-Id},
 * and logs a {@code client.request} line before and a {@code client.response} line after
 * (status, duration, redacted body). A request body cannot be read back from an
 * {@link HttpRequest}, so callers pass the string they built it from.
 */
public final class LoggedHttp {

    private final HttpClient client;
    private final JsonLogger log;
    private final String target;

    /**
     * @param target short name of the remote system for the logs, e.g. "product-api" or "opensearch"
     */
    public LoggedHttp(HttpClient client, String service, String target) {
        this.client = client;
        this.log = new JsonLogger(service);
        this.target = target;
    }

    public HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return send(request, null);
    }

    public HttpResponse<String> send(HttpRequest request, String requestBody) throws IOException, InterruptedException {
        HttpRequest outgoing = HttpRequest.newBuilder(request, (k, v) -> true)
                .setHeader(Correlation.HEADER, Correlation.current())
                .build();

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("target", target);
        req.put("method", outgoing.method());
        req.put("url", Redactor.sanitizeText(outgoing.uri().toString()));
        req.put("body", Redactor.body(requestBody));
        log.event("client.request", "Calling " + target, req);

        long start = System.nanoTime();
        try {
            HttpResponse<String> response = client.send(outgoing, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("target", target);
            res.put("method", outgoing.method());
            res.put("url", Redactor.sanitizeText(outgoing.uri().toString()));
            res.put("status", response.statusCode());
            res.put("durationMs", (System.nanoTime() - start) / 1_000_000);
            res.put("body", Redactor.body(response.body()));
            LogConfig.Level level = response.statusCode() >= 500 ? LogConfig.Level.ERROR
                    : response.statusCode() >= 400 ? LogConfig.Level.WARN : LogConfig.Level.INFO;
            log.event(level, "client.response", target + " responded " + response.statusCode(), res);
            return response;
        } catch (IOException | InterruptedException | RuntimeException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("target", target);
            err.put("method", outgoing.method());
            err.put("url", Redactor.sanitizeText(outgoing.uri().toString()));
            err.put("durationMs", (System.nanoTime() - start) / 1_000_000);
            err.put("errorType", e.getClass().getName());
            err.put("errorMessage", Redactor.sanitizeText(String.valueOf(e.getMessage())));
            log.event(LogConfig.Level.ERROR, "client.response", target + " call failed", err);
            throw e;
        }
    }
}

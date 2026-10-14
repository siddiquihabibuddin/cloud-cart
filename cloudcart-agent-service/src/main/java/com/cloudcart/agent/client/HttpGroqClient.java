package com.cloudcart.agent.client;

import com.cloudcart.agent.model.ChatCompletionResponse;
import com.cloudcart.agent.model.Message;
import com.cloudcart.common.log.JsonLogger;
import com.cloudcart.common.log.LogConfig;
import com.cloudcart.common.log.Redactor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpGroqClient implements GroqClient {

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final String url;
    private final HttpClient httpClient;
    private final JsonLogger log = new JsonLogger("agent-service");

    public HttpGroqClient(ObjectMapper mapper, String apiKey, String model) {
        this(mapper, apiKey, model, GROQ_URL, HTTP_CLIENT);
    }

    /** Test seam: point the client at a local server. */
    HttpGroqClient(ObjectMapper mapper, String apiKey, String model, String url, HttpClient httpClient) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
        this.url = url;
        this.httpClient = httpClient;
    }

    @Override
    public ChatCompletionResponse complete(List<Message> messages, List<Map<String, Object>> tools) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages,
                "tools", tools,
                "tool_choice", "auto");
        String json = mapper.writeValueAsString(requestBody);

        // The 9 tool schemas are large and static, so only their names are logged.
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", model);
        req.put("messageCount", messages.size());
        req.put("toolNames", toolNames(tools));
        req.put("requestBytes", json.length());
        req.put("body", Redactor.body(mapper.writeValueAsString(messages)));
        log.event("llm.request", "Calling Groq", req);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        long start = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            logFailure("Timed out calling Groq API", start, e);
            throw new GroqException("Timed out calling Groq API", e);
        } catch (Exception e) {
            logFailure("Groq call failed", start, e);
            throw e;
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("status", response.statusCode());
        res.put("durationMs", (System.nanoTime() - start) / 1_000_000);
        response.headers().firstValue("x-ratelimit-remaining-tokens").ifPresent(v -> res.put("rateLimitRemainingTokens", v));
        response.headers().firstValue("x-ratelimit-remaining-requests").ifPresent(v -> res.put("rateLimitRemainingRequests", v));
        response.headers().firstValue("retry-after").ifPresent(v -> res.put("retryAfter", v));
        res.put("body", Redactor.body(response.body()));

        if (response.statusCode() != 200) {
            log.event(response.statusCode() >= 500 || response.statusCode() == 429 ? LogConfig.Level.ERROR : LogConfig.Level.WARN,
                    "llm.response", "Groq returned " + response.statusCode(), res);
            throw new GroqException("Groq API returned " + response.statusCode() + ": " + response.body());
        }

        ChatCompletionResponse parsed = mapper.readValue(response.body(), ChatCompletionResponse.class);
        res.put("model", parsed.getModel());
        res.put("completionId", parsed.getId());
        if (parsed.getUsage() != null) {
            res.put("promptTokens", parsed.getUsage().getPromptTokens());
            res.put("completionTokens", parsed.getUsage().getCompletionTokens());
            res.put("totalTokens", parsed.getUsage().getTotalTokens());
        }
        if (parsed.getChoices() != null && !parsed.getChoices().isEmpty()) {
            res.put("finishReason", parsed.getChoices().get(0).getFinishReason());
        }
        log.event("llm.response", "Groq responded", res);
        return parsed;
    }

    private void logFailure(String message, long startNanos, Exception e) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("durationMs", (System.nanoTime() - startNanos) / 1_000_000);
        err.put("errorType", e.getClass().getName());
        err.put("errorMessage", Redactor.sanitizeText(String.valueOf(e.getMessage())));
        log.event(LogConfig.Level.ERROR, "llm.response", message, err);
    }

    @SuppressWarnings("unchecked")
    private static List<String> toolNames(List<Map<String, Object>> tools) {
        List<String> names = new ArrayList<>();
        if (tools == null) return names;
        for (Map<String, Object> tool : tools) {
            Object fn = tool.get("function");
            if (fn instanceof Map<?, ?> m && m.get("name") != null) {
                names.add(m.get("name").toString());
            }
        }
        return names;
    }
}

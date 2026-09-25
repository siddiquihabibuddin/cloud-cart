package com.cloudcart.agent.client;

import com.cloudcart.agent.model.ChatCompletionResponse;
import com.cloudcart.agent.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
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

    public HttpGroqClient(ObjectMapper mapper, String apiKey, String model) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public ChatCompletionResponse complete(List<Message> messages, List<Map<String, Object>> tools) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages,
                "tools", tools,
                "tool_choice", "auto");
        String json = mapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new GroqException("Timed out calling Groq API", e);
        }

        if (response.statusCode() != 200) {
            throw new GroqException("Groq API returned " + response.statusCode() + ": " + response.body());
        }

        return mapper.readValue(response.body(), ChatCompletionResponse.class);
    }
}

package com.cloudcart.agent.client;

import com.cloudcart.agent.LogCapture;
import com.cloudcart.agent.model.ChatCompletionResponse;
import com.cloudcart.agent.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpGroqClientTest {

    private static final String SECRET_KEY = "gsk_SUPERSECRETKEY1234567890";

    private LogCapture logs;
    private HttpServer server;
    private volatile int status = 200;
    private volatile String body = "{\"id\":\"cmpl-1\",\"model\":\"openai/gpt-oss-120b\","
            + "\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"hi\"}}],"
            + "\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":30,\"total_tokens\":150}}";

    @BeforeEach
    void setUp() throws Exception {
        logs = LogCapture.start();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("x-ratelimit-remaining-tokens", "7421");
            exchange.getResponseHeaders().add("retry-after", "3");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        LogCapture.stop();
    }

    private HttpGroqClient client() {
        return new HttpGroqClient(new ObjectMapper(), SECRET_KEY, "openai/gpt-oss-120b",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat", HttpClient.newHttpClient());
    }

    private static final List<Map<String, Object>> TOOLS = List.of(
            Map.of("type", "function", "function", Map.of("name", "search_products", "description", "huge schema...")),
            Map.of("type", "function", "function", Map.of("name", "view_cart", "description", "another")));

    @Test
    void parsesUsageAndLogsRequestAndResponseWithTokenCounts() throws Exception {
        ChatCompletionResponse response = client().complete(List.of(new Message("user", "find a lamp")), TOOLS);

        assertEquals(150, response.getUsage().getTotalTokens());
        assertEquals("stop", response.getChoices().get(0).getFinishReason());

        Map<String, Object> req = logs.event("llm.request");
        assertEquals(1, ((Number) req.get("messageCount")).intValue());
        assertEquals(List.of("search_products", "view_cart"), req.get("toolNames"));
        assertTrue(req.get("body").toString().contains("find a lamp"));
        assertFalse(req.get("body").toString().contains("huge schema"), "tool schemas must not be logged");

        Map<String, Object> res = logs.event("llm.response");
        assertEquals(200, ((Number) res.get("status")).intValue());
        assertEquals(120, ((Number) res.get("promptTokens")).intValue());
        assertEquals(30, ((Number) res.get("completionTokens")).intValue());
        assertEquals(150, ((Number) res.get("totalTokens")).intValue());
        assertEquals("stop", res.get("finishReason"));
        assertEquals("openai/gpt-oss-120b", res.get("model"));
        assertEquals("7421", res.get("rateLimitRemainingTokens"));
        assertTrue(((Number) res.get("durationMs")).longValue() >= 0);
    }

    @Test
    void neverLogsTheApiKeyEvenWhenAMessageContainsIt() throws Exception {
        client().complete(List.of(new Message("user", "my key is " + SECRET_KEY)), TOOLS);

        assertFalse(logs.anyLineContains(SECRET_KEY));
    }

    @Test
    void rateLimitResponsesAreLoggedAsErrorsWithTheBodyAndThrown() {
        status = 429;
        body = "{\"error\":{\"message\":\"Rate limit reached for tokens per minute\"}}";

        GroqException e = assertThrows(GroqException.class,
                () -> client().complete(List.of(new Message("user", "hi")), TOOLS));

        assertTrue(e.getMessage().contains("429"));
        Map<String, Object> res = logs.event("llm.response");
        assertEquals("ERROR", res.get("level"));
        assertEquals(429, ((Number) res.get("status")).intValue());
        assertEquals("3", res.get("retryAfter"));
        assertTrue(res.get("body").toString().contains("Rate limit reached"));
    }

    @Test
    void connectionFailuresAreLogged() {
        server.stop(0);

        assertThrows(Exception.class, () -> client().complete(List.of(new Message("user", "hi")), TOOLS));

        Map<String, Object> res = logs.event("llm.response");
        assertEquals("ERROR", res.get("level"));
        assertTrue(res.containsKey("errorType"));
    }
}

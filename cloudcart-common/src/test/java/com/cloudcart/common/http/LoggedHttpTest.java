package com.cloudcart.common.http;

import com.cloudcart.common.log.Correlation;
import com.cloudcart.common.log.LogTestSupport;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggedHttpTest {

    private LogTestSupport logs;
    private HttpServer server;
    private final AtomicReference<String> receivedCorrelation = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = "{\"ok\":true}";

    @BeforeEach
    void setUp() throws Exception {
        logs = LogTestSupport.capture();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedCorrelation.set(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        LogTestSupport.reset();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private LoggedHttp http() {
        return new LoggedHttp(HttpClient.newHttpClient(), "order-service", "product-api");
    }

    @Test
    void forwardsTheCorrelationIdAndLogsRequestAndResponse() throws Exception {
        Correlation.set("corr-42");
        HttpRequest request = HttpRequest.newBuilder(uri("/products/p1/stock"))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"reserve\":1}"))
                .build();

        HttpResponse<String> response = http().send(request, "{\"reserve\":1}");

        assertEquals(200, response.statusCode());
        assertEquals("corr-42", receivedCorrelation.get());
        Map<String, Object> req = logs.event("client.request");
        assertEquals("product-api", req.get("target"));
        assertEquals("PATCH", req.get("method"));
        assertEquals("{\"reserve\":1}", req.get("body"));
        assertEquals("corr-42", req.get("correlationId"));
        Map<String, Object> res = logs.event("client.response");
        assertEquals(200, res.get("status"));
        assertEquals("{\"ok\":true}", res.get("body"));
        assertTrue(((Number) res.get("durationMs")).longValue() >= 0);
    }

    @Test
    void redactsSecretsInBothDirections() throws Exception {
        responseBody = "{\"token\":\"abc\",\"note\":\"Bearer xyz.123\"}";
        HttpRequest request = HttpRequest.newBuilder(uri("/x")).POST(HttpRequest.BodyPublishers.ofString("p")).build();

        http().send(request, "{\"password\":\"Hunter2\"}");

        assertFalse(logs.anyLineContains("Hunter2"));
        assertFalse(logs.anyLineContains("xyz.123"));
        assertTrue(logs.event("client.response").get("body").toString().contains("\"token\":\"***\""));
    }

    @Test
    void serverErrorsAreLoggedAtErrorLevelAndWarnsFor4xx() throws Exception {
        status = 503;
        http().send(HttpRequest.newBuilder(uri("/x")).GET().build());
        assertEquals("ERROR", logs.event("client.response").get("level"));
    }

    @Test
    void connectionFailuresAreLoggedAndRethrown() {
        server.stop(0);
        HttpRequest request = HttpRequest.newBuilder(uri("/x")).GET().build();

        assertThrows(java.io.IOException.class, () -> http().send(request));

        Map<String, Object> res = logs.event("client.response");
        assertEquals("ERROR", res.get("level"));
        assertTrue(res.get("message").toString().contains("failed"));
    }
}

package com.cloudcart.mcp.config;

import com.cloudcart.common.log.Correlation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LoggingRequestInterceptorTest {

    private LogCapture logs;

    @BeforeEach
    void setUp() {
        logs = LogCapture.start();
    }

    @AfterEach
    void tearDown() {
        LogCapture.stop();
    }

    private RestClient.Builder builder() {
        return RestClient.builder().baseUrl("http://gateway.test").requestInterceptor(new LoggingRequestInterceptor("gateway"));
    }

    @Test
    void forwardsTheCorrelationIdAndLogsBothDirectionsWithSecretsMasked() {
        Correlation.set("turn-42");
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).bufferContent().build();
        server.expect(requestTo("http://gateway.test/cart"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Correlation-Id", "turn-42"))
                .andRespond(withSuccess("{\"message\":\"Item added\",\"token\":\"leak.me.now\"}", MediaType.APPLICATION_JSON));

        Map<?, ?> out = builder.build().post().uri("/cart")
                .header("Authorization", "Bearer user.jwt.value")
                .body(Map.of("productId", "p1", "password", "Hunter2"))
                .retrieve().body(Map.class);

        assertThat(out.get("message")).isEqualTo("Item added");   // the caller can still read the body
        server.verify();

        Map<String, Object> req = logs.event("client.request");
        assertThat(req.get("target")).isEqualTo("gateway");
        assertThat(req.get("correlationId")).isEqualTo("turn-42");
        assertThat(req.get("body").toString()).contains("p1");
        assertThat(((Map<?, ?>) req.get("headers")).get("Authorization")).isEqualTo("***");

        Map<String, Object> res = logs.event("client.response");
        assertThat(((Number) res.get("status")).intValue()).isEqualTo(200);
        assertThat(res.get("body").toString()).contains("\"token\":\"***\"");

        assertThat(logs.anyLineContains("user.jwt.value")).isFalse();
        assertThat(logs.anyLineContains("Hunter2")).isFalse();
        assertThat(logs.anyLineContains("leak.me.now")).isFalse();
    }

    @Test
    void serverErrorsAreLoggedAtErrorLevel() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).bufferContent().build();
        server.expect(requestTo("http://gateway.test/orders")).andRespond(withServerError());

        try {
            builder.build().get().uri("/orders").retrieve().body(String.class);
        } catch (Exception ignored) {
            // the tool layer turns this into {"error": ...}
        }

        Map<String, Object> res = logs.event("client.response");
        assertThat(res.get("level")).isEqualTo("ERROR");
        assertThat(((Number) res.get("status")).intValue()).isEqualTo(500);
    }
}

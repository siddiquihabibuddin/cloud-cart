package com.cloudcart.mcp.config;

import com.cloudcart.common.log.Correlation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationFilterTest {

    private LogCapture logs;

    @BeforeEach
    void setUp() {
        logs = LogCapture.start();
    }

    @AfterEach
    void tearDown() {
        LogCapture.stop();
    }

    private MockHttpServletRequest mcpRequest(String correlationId, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        if (correlationId != null) request.addHeader("X-Correlation-Id", correlationId);
        request.setContent(body.getBytes());
        return request;
    }

    @Test
    void bindsTheCallersIdForTheDurationOfTheRequestAndEchoesIt() throws Exception {
        AtomicReference<String> seenDuringRequest = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorrelationFilter().doFilter(mcpRequest("turn-7", "{\"method\":\"tools/list\"}"), response,
                new MockFilterChain() {
                    @Override
                    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse res)
                            throws java.io.IOException, jakarta.servlet.ServletException {
                        // the transport consumes the body, which is what makes it available to log
                        request.getInputStream().readAllBytes();
                        seenDuringRequest.set(Correlation.current());
                        super.doFilter(request, res);
                    }
                });

        assertThat(seenDuringRequest.get()).isEqualTo("turn-7");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("turn-7");
    }

    @Test
    void logsTheInboundJsonRpcCallWithTheTokenMaskedAndTheStatus() throws Exception {
        String rpc = "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"view_cart\","
                + "\"arguments\":{\"userId\":\"u1\",\"authToken\":\"user.jwt.value\"}}}";
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        new CorrelationFilter().doFilter(mcpRequest("turn-8", rpc), response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse res)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                request.getInputStream().readAllBytes();
                super.doFilter(request, res);
            }
        });

        Map<String, Object> line = logs.event("http.in");
        assertThat(line.get("correlationId")).isEqualTo("turn-8");
        assertThat(line.get("method")).isEqualTo("POST");
        assertThat(line.get("path")).isEqualTo("/mcp");
        assertThat(((Number) line.get("status")).intValue()).isEqualTo(200);
        assertThat(line.get("body").toString()).contains("tools/call").contains("\"authToken\":\"***\"");
        assertThat(logs.anyLineContains("user.jwt.value")).isFalse();
    }

    @Test
    void generatesAnIdWhenTheCallerSentNone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorrelationFilter().doFilter(mcpRequest(null, "{}"), response, new MockFilterChain());

        assertThat(response.getHeader("X-Correlation-Id")).isNotBlank();
    }
}

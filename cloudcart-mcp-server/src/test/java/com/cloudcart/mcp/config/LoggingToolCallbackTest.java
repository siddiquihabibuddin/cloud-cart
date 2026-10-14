package com.cloudcart.mcp.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoggingToolCallbackTest {

    private LogCapture logs;

    @BeforeEach
    void setUp() {
        logs = LogCapture.start();
    }

    @AfterEach
    void tearDown() {
        LogCapture.stop();
    }

    private static ToolCallback tool(String name, Function<String, String> body) {
        ToolDefinition definition = ToolDefinition.builder().name(name).description("d").inputSchema("{}").build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                return body.apply(toolInput);
            }
        };
    }

    @Test
    void logsArgumentsAndResultAndMasksTheCallersToken() {
        ToolCallback logged = new LoggingToolCallback(tool("view_cart", in -> "[{\"productId\":\"p1\"}]"));

        String result = logged.call("{\"userId\":\"u1\",\"authToken\":\"super.secret.jwt\"}");

        assertThat(result).isEqualTo("[{\"productId\":\"p1\"}]");
        Map<String, Object> line = logs.event("tool.call");
        assertThat(line.get("tool")).isEqualTo("view_cart");
        assertThat(line.get("failed")).isEqualTo(false);
        assertThat(line.get("arguments").toString()).contains("\"authToken\":\"***\"").contains("\"userId\":\"u1\"");
        assertThat(line.get("result").toString()).contains("p1");
        assertThat(((Number) line.get("durationMs")).longValue()).isGreaterThanOrEqualTo(0);
        assertThat(logs.anyLineContains("super.secret.jwt")).isFalse();
    }

    @Test
    void errorMapsReturnedByToolsAreLoggedAsWarnings() {
        ToolCallback logged = new LoggingToolCallback(tool("place_order", in -> "{\"error\":\"404 Not Found\"}"));

        logged.call("{}");

        Map<String, Object> line = logs.event("tool.call");
        assertThat(line.get("level")).isEqualTo("WARN");
        assertThat(line.get("failed")).isEqualTo(true);
    }

    @Test
    void exceptionsAreLoggedAtErrorLevelAndRethrownUnchanged() {
        ToolCallback logged = new LoggingToolCallback(tool("view_cart", in -> {
            throw new IllegalStateException("boom");
        }));

        assertThatThrownBy(() -> logged.call("{}")).isInstanceOf(IllegalStateException.class).hasMessage("boom");

        Map<String, Object> line = logs.event("tool.call");
        assertThat(line.get("level")).isEqualTo("ERROR");
        assertThat(line.get("errorMessage")).isEqualTo("boom");
    }

    @Test
    void delegatesTheToolDefinitionUntouched() {
        ToolCallback inner = tool("search_products", in -> "{}");
        assertThat(new LoggingToolCallback(inner).getToolDefinition().name()).isEqualTo("search_products");
    }
}

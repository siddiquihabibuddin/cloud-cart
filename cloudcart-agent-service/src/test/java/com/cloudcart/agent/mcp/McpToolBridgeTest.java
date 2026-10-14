package com.cloudcart.agent.mcp;

import com.cloudcart.agent.LogCapture;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpToolBridgeTest {

    private LogCapture logs;
    private McpSyncClient client;
    private McpToolBridge bridge;

    @BeforeEach
    void setUp() {
        logs = LogCapture.start();
        client = mock(McpSyncClient.class);
        bridge = new McpToolBridge(client, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        LogCapture.stop();
    }

    private static McpSchema.CallToolResult result(String text, boolean isError) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(text)), isError, null, null);
    }

    @Test
    void logsToolCallRequestAndResponseWithAuthTokenMasked() throws Exception {
        when(client.callTool(any())).thenReturn(result("[{\"productId\":\"p1\"}]", false));

        String out = bridge.callTool("view_cart",
                "{\"userId\":\"u1\",\"authToken\":\"super.secret.value\"}");

        assertEquals("[{\"productId\":\"p1\"}]", out);
        Map<String, Object> req = logs.event("mcp.request");
        assertEquals("tools/call", req.get("method"));
        assertEquals("view_cart", req.get("tool"));
        assertTrue(req.get("arguments").toString().contains("\"authToken\":\"***\""));
        assertTrue(req.get("arguments").toString().contains("\"userId\":\"u1\""));

        Map<String, Object> res = logs.event("mcp.response");
        assertEquals("view_cart", res.get("tool"));
        assertEquals(false, res.get("isError"));
        assertTrue(res.get("body").toString().contains("p1"));
        assertTrue(((Number) res.get("durationMs")).longValue() >= 0);
        assertFalse(logs.anyLineContains("super.secret.value"));
    }

    @Test
    void toolErrorResultsAreLoggedAsWarnings() throws Exception {
        when(client.callTool(any())).thenReturn(result("{\"error\":\"out of stock\"}", true));

        bridge.callTool("place_order", "{}");

        Map<String, Object> res = logs.event("mcp.response");
        assertEquals(true, res.get("isError"));
        assertEquals("WARN", res.get("level"));
    }

    @Test
    void transportFailuresAreLoggedThenRethrown() {
        when(client.callTool(any())).thenThrow(new IllegalStateException("MCP server unreachable"));

        assertThrows(IllegalStateException.class, () -> bridge.callTool("view_cart", "{}"));

        Map<String, Object> res = logs.event("mcp.response");
        assertEquals("ERROR", res.get("level"));
        assertEquals("tools/call", res.get("method"));
        assertEquals("MCP server unreachable", res.get("errorMessage"));
    }

    @Test
    void logsToolDiscovery() {
        McpSchema.Tool tool = mock(McpSchema.Tool.class);
        when(tool.name()).thenReturn("search_products");
        when(tool.description()).thenReturn("search");
        when(tool.inputSchema()).thenReturn(Map.of());
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool), null));

        List<Map<String, Object>> tools = bridge.listGroqTools();

        assertEquals(1, tools.size());
        assertEquals("tools/list", logs.event("mcp.request").get("method"));
        Map<String, Object> res = logs.event("mcp.response");
        assertEquals(1, ((Number) res.get("toolCount")).intValue());
        assertEquals(List.of("search_products"), res.get("toolNames"));
    }
}

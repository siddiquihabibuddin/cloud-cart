package com.cloudcart.agent.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class McpToolBridge implements ToolBridge {

    private final McpSyncClient client;
    private final ObjectMapper mapper;

    public McpToolBridge(String mcpServerUrl, ObjectMapper mapper) {
        this.mapper = mapper;
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(mcpServerUrl)
                .jsonMapper(new JacksonMcpJsonMapper(mapper))
                .build();
        this.client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(20))
                .build();
        this.client.initialize();
    }

    McpToolBridge(McpSyncClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public List<Map<String, Object>> listGroqTools() {
        List<McpSchema.Tool> tools = client.listTools().tools();
        List<Map<String, Object>> groqTools = new ArrayList<>();
        for (McpSchema.Tool tool : tools) {
            groqTools.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", tool.name(),
                            "description", tool.description() == null ? "" : tool.description(),
                            "parameters", tool.inputSchema())));
        }
        return groqTools;
    }

    @Override
    public String callTool(String name, String argumentsJson) throws Exception {
        Map<String, Object> arguments = argumentsJson == null || argumentsJson.isBlank()
                ? Map.of()
                : mapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {});

        McpSchema.CallToolResult result = client.callTool(
                McpSchema.CallToolRequest.builder(name).arguments(arguments).build());

        StringBuilder text = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent) {
                text.append(textContent.text());
            }
        }
        return text.toString();
    }
}

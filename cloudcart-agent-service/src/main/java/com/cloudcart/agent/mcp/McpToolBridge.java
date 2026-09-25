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
import java.util.LinkedHashMap;
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
                            "parameters", withNullableOptionalFields(tool.inputSchema()))));
        }
        return groqTools;
    }

    /**
     * Groq's tool-calling occasionally emits an explicit `null` for an optional
     * parameter it decided not to use, instead of omitting the key entirely. A plain
     * "type": "number" schema (Spring AI's default for an optional field) rejects that
     * as invalid, which fails the whole tool call. Widening every non-required
     * property's type to also allow null keeps optional parameters truly optional.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> withNullableOptionalFields(Map<String, Object> schema) {
        if (schema == null || !(schema.get("properties") instanceof Map)) {
            return schema;
        }
        Map<String, Object> result = new LinkedHashMap<>(schema);
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        List<?> required = schema.get("required") instanceof List ? (List<?>) schema.get("required") : List.of();

        Map<String, Object> newProperties = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String propName = entry.getKey();
            if (required.contains(propName) || !(entry.getValue() instanceof Map)) {
                newProperties.put(propName, entry.getValue());
                continue;
            }
            Map<String, Object> propSchema = new LinkedHashMap<>((Map<String, Object>) entry.getValue());
            if (propSchema.get("type") instanceof String type && !type.equals("null")) {
                propSchema.put("type", List.of(type, "null"));
            }
            newProperties.put(propName, propSchema);
        }
        result.put("properties", newProperties);
        return result;
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

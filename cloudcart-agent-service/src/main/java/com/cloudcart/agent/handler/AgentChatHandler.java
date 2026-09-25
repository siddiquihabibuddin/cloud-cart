package com.cloudcart.agent.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.agent.client.GroqClient;
import com.cloudcart.agent.client.HttpGroqClient;
import com.cloudcart.agent.mcp.McpToolBridge;
import com.cloudcart.agent.mcp.ToolBridge;
import com.cloudcart.agent.model.ChatCompletionResponse;
import com.cloudcart.agent.model.Message;
import com.cloudcart.agent.model.ToolCall;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AgentChatHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final int MAX_TOOL_ITERATIONS = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GroqClient groqClient;
    private final ToolBridge toolBridge;

    public AgentChatHandler() {
        this(
                new HttpGroqClient(MAPPER, System.getenv("GROQ_API_KEY"), System.getenv("GROQ_MODEL")),
                new McpToolBridge(System.getenv("MCP_SERVER_URL"), MAPPER));
    }

    AgentChatHandler(GroqClient groqClient, ToolBridge toolBridge) {
        this.groqClient = groqClient;
        this.toolBridge = toolBridge;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            String body = (String) input.get("body");
            Map<String, Object> requestBody = MAPPER.readValue(body, new TypeReference<>() {});

            String userId = (String) requestBody.get("userId");
            String userMessage = (String) requestBody.get("message");
            if (userId == null || userId.isBlank()) {
                return response(400, "{\"error\":\"userId is required\"}");
            }
            if (userMessage == null || userMessage.isBlank()) {
                return response(400, "{\"error\":\"message is required\"}");
            }

            List<Message> history = requestBody.get("history") != null
                    ? MAPPER.convertValue(requestBody.get("history"), new TypeReference<List<Message>>() {})
                    : new ArrayList<>();

            List<Message> messages = new ArrayList<>();
            messages.add(new Message("system", systemPrompt(userId)));
            messages.addAll(history);
            messages.add(new Message("user", userMessage));

            List<Map<String, Object>> tools = toolBridge.listGroqTools();

            String reply = null;
            for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
                ChatCompletionResponse completion = groqClient.complete(messages, tools);
                Message assistantMessage = completion.getChoices().get(0).getMessage();
                messages.add(assistantMessage);

                List<ToolCall> toolCalls = assistantMessage.getToolCalls();
                if (toolCalls == null || toolCalls.isEmpty()) {
                    reply = assistantMessage.getContent();
                    break;
                }

                for (ToolCall toolCall : toolCalls) {
                    String result;
                    try {
                        result = toolBridge.callTool(
                                toolCall.getFunction().getName(),
                                toolCall.getFunction().getArguments());
                    } catch (Exception toolEx) {
                        result = MAPPER.writeValueAsString(Map.of("error", String.valueOf(toolEx.getMessage())));
                    }
                    messages.add(Message.tool(toolCall.getId(), result));
                }
            }

            if (reply == null) {
                reply = "Sorry, I wasn't able to finish that request - could you try rephrasing it?";
                messages.add(new Message("assistant", reply));
            }

            List<Message> newHistory = messages.subList(1, messages.size());
            String responseBody = MAPPER.writeValueAsString(Map.of("reply", reply, "history", newHistory));
            return response(200, responseBody);
        } catch (Exception e) {
            context.getLogger().log("AgentChatHandler error: " + e.getMessage());
            return response(500, "{\"error\":\"Failed to process chat message\"}");
        }
    }

    private String systemPrompt(String userId) {
        return "You are CloudCart's shopping assistant. Use the available tools to look up products, "
                + "manage the user's cart, and check or place orders. Never invent product ids, prices, or "
                + "order ids - always look them up first. Keep replies short and friendly. "
                + "The current user's id is \"" + userId + "\" - use it automatically for any tool that "
                + "needs a userId, and never ask the user for it.";
    }

    private Map<String, Object> response(int statusCode, String body) {
        return Map.of(
                "statusCode", statusCode,
                "headers", Map.of("Content-Type", "application/json"),
                "body", body
        );
    }
}

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
import com.cloudcart.agent.util.JwtVerificationException;
import com.cloudcart.agent.util.JwtVerifier;
import com.cloudcart.agent.util.NimbusJwtVerifier;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AgentChatHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final int MAX_TOOL_ITERATIONS = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GroqClient groqClient;
    private final ToolBridge toolBridge;
    private final JwtVerifier jwtVerifier;

    public AgentChatHandler() {
        this(
                new HttpGroqClient(MAPPER, System.getenv("GROQ_API_KEY"), System.getenv("GROQ_MODEL")),
                new McpToolBridge(System.getenv("MCP_SERVER_URL"), MAPPER),
                new NimbusJwtVerifier(System.getenv("JWT_SECRET")));
    }

    AgentChatHandler(GroqClient groqClient, ToolBridge toolBridge, JwtVerifier jwtVerifier) {
        this.groqClient = groqClient;
        this.toolBridge = toolBridge;
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            String rawToken = extractBearerToken((Map<String, Object>) input.get("headers"));
            if (rawToken == null) {
                return response(401, "{\"error\":\"Unauthorized\"}");
            }
            String authenticatedUserId;
            try {
                authenticatedUserId = jwtVerifier.verify(rawToken);
            } catch (JwtVerificationException e) {
                return response(401, "{\"error\":\"Unauthorized\"}");
            }

            String body = (String) input.get("body");
            Map<String, Object> requestBody = MAPPER.readValue(body, new TypeReference<>() {});

            String userMessage = (String) requestBody.get("message");
            if (userMessage == null || userMessage.isBlank()) {
                return response(400, "{\"error\":\"message is required\"}");
            }

            List<Message> history = requestBody.get("history") != null
                    ? MAPPER.convertValue(requestBody.get("history"), new TypeReference<List<Message>>() {})
                    : new ArrayList<>();

            List<Message> messages = new ArrayList<>();
            messages.add(new Message("system", systemPrompt(authenticatedUserId)));
            messages.addAll(history);
            messages.add(new Message("user", userMessage));

            List<Map<String, Object>> tools = toolBridge.listGroqTools();
            Map<String, Set<String>> toolParameterNames = indexToolParameterNames(tools);

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
                        Set<String> allowedParams = toolParameterNames.getOrDefault(
                                toolCall.getFunction().getName(), Set.of());
                        String trustedArgs = withTrustedIdentity(
                                toolCall.getFunction().getArguments(), authenticatedUserId, rawToken, allowedParams);
                        result = toolBridge.callTool(toolCall.getFunction().getName(), trustedArgs);
                    } catch (Exception toolEx) {
                        result = MAPPER.writeValueAsString(Map.of("error", String.valueOf(toolEx.getMessage())));
                    }
                    context.getLogger().log("tool_call name=" + toolCall.getFunction().getName()
                            + " args=" + toolCall.getFunction().getArguments()
                            + " result=" + result);
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

    /**
     * Regardless of what the model decided to pass, every tool call is forced to act
     * as the caller who was actually authenticated on this request - closes the gap
     * where a steered conversation could otherwise ask the assistant to act as a
     * different user. Only set fields the tool's own schema declares (e.g. catalog
     * tools like search_products take neither) - MCP's schema validation rejects
     * additional properties it doesn't recognize.
     */
    private String withTrustedIdentity(
            String argumentsJson, String userId, String authToken, Set<String> allowedParams) throws Exception {
        Map<String, Object> args = argumentsJson == null || argumentsJson.isBlank()
                ? new HashMap<>()
                : new HashMap<>(MAPPER.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {}));
        if (allowedParams.contains("userId")) {
            args.put("userId", userId);
        }
        if (allowedParams.contains("authToken")) {
            args.put("authToken", authToken);
        }
        return MAPPER.writeValueAsString(args);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> indexToolParameterNames(List<Map<String, Object>> tools) {
        Map<String, Set<String>> index = new HashMap<>();
        for (Map<String, Object> tool : tools) {
            Map<String, Object> function = (Map<String, Object>) tool.get("function");
            String name = (String) function.get("name");
            Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
            Object properties = parameters == null ? null : parameters.get("properties");
            index.put(name, properties instanceof Map
                    ? ((Map<String, Object>) properties).keySet()
                    : Set.of());
        }
        return index;
    }

    private String systemPrompt(String userId) {
        return "You are CloudCart's shopping assistant. Use the available tools to look up products, "
                + "manage the user's cart, and check or place orders. Never invent product ids, prices, or "
                + "order ids - always look them up first. Keep replies short and friendly. "
                + "The current user's id is \"" + userId + "\".";
    }

    private String extractBearerToken(Map<String, Object> headers) {
        if (headers == null) return null;
        Object val = headers.get("Authorization");
        if (val == null) val = headers.get("authorization");
        if (val == null) return null;
        String header = val.toString();
        return header.startsWith("Bearer ") ? header.substring("Bearer ".length()) : null;
    }

    private Map<String, Object> response(int statusCode, String body) {
        return Map.of(
                "statusCode", statusCode,
                "headers", Map.of("Content-Type", "application/json"),
                "body", body
        );
    }
}

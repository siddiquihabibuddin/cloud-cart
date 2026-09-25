package com.cloudcart.agent.handler;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.cloudcart.agent.client.GroqClient;
import com.cloudcart.agent.mcp.ToolBridge;
import com.cloudcart.agent.model.ChatCompletionResponse;
import com.cloudcart.agent.model.FunctionCall;
import com.cloudcart.agent.model.Message;
import com.cloudcart.agent.model.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void chainsAToolCallThenReturnsAFinalReply() throws Exception {
        GroqClient groqClient = mock(GroqClient.class);
        ToolBridge toolBridge = mock(ToolBridge.class);

        when(toolBridge.listGroqTools()).thenReturn(List.of(
                Map.of("type", "function", "function", Map.of("name", "search_products"))));

        ToolCall searchCall = new ToolCall();
        searchCall.setId("call_1");
        searchCall.setType("function");
        FunctionCall function = new FunctionCall();
        function.setName("search_products");
        function.setArguments("{\"query\":\"laptop\",\"maxPrice\":800}");
        searchCall.setFunction(function);

        Message assistantToolCallMessage = new Message("assistant", null);
        assistantToolCallMessage.setToolCalls(List.of(searchCall));

        Message finalAssistantMessage = new Message("assistant", "I found a laptop under $800 for you.");

        when(groqClient.complete(anyList(), anyList()))
                .thenReturn(completionOf(assistantToolCallMessage))
                .thenReturn(completionOf(finalAssistantMessage));

        when(toolBridge.callTool(eq("search_products"), anyString()))
                .thenReturn("{\"products\":[{\"productId\":\"p1\",\"title\":\"Budget Laptop\",\"price\":750}]}");

        AgentChatHandler handler = new AgentChatHandler(groqClient, toolBridge);

        Map<String, Object> input = Map.of(
                "body", "{\"userId\":\"u1\",\"message\":\"find me a laptop under $800\"}");

        Map<String, Object> result = handler.handleRequest(input, new NoOpContext());

        assertEquals(200, result.get("statusCode"));
        Map<?, ?> body = MAPPER.readValue((String) result.get("body"), Map.class);
        assertEquals("I found a laptop under $800 for you.", body.get("reply"));

        verify(toolBridge, times(1)).callTool(eq("search_products"), anyString());
        verify(groqClient, times(2)).complete(anyList(), anyList());
    }

    @Test
    void rejectsAMissingMessage() throws Exception {
        GroqClient groqClient = mock(GroqClient.class);
        ToolBridge toolBridge = mock(ToolBridge.class);
        AgentChatHandler handler = new AgentChatHandler(groqClient, toolBridge);

        Map<String, Object> input = Map.of("body", "{\"userId\":\"u1\"}");
        Map<String, Object> result = handler.handleRequest(input, new NoOpContext());

        assertEquals(400, result.get("statusCode"));
    }

    private ChatCompletionResponse completionOf(Message message) {
        ChatCompletionResponse response = new ChatCompletionResponse();
        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        choice.setMessage(message);
        response.setChoices(List.of(choice));
        return response;
    }

    private static class NoOpContext implements Context {
        @Override public String getAwsRequestId() { return "test"; }
        @Override public String getLogGroupName() { return "test"; }
        @Override public String getLogStreamName() { return "test"; }
        @Override public String getFunctionName() { return "test"; }
        @Override public String getFunctionVersion() { return "test"; }
        @Override public String getInvokedFunctionArn() { return "test"; }
        @Override public CognitoIdentity getIdentity() { return null; }
        @Override public ClientContext getClientContext() { return null; }
        @Override public int getRemainingTimeInMillis() { return 10000; }
        @Override public int getMemoryLimitInMB() { return 128; }

        @Override
        public LambdaLogger getLogger() {
            return new LambdaLogger() {
                @Override public void log(String message) {}
                @Override public void log(byte[] message) {}
            };
        }
    }
}

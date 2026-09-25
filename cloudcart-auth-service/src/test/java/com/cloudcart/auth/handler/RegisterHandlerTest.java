package com.cloudcart.auth.handler;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.cloudcart.auth.repository.UserRepository;
import com.cloudcart.auth.util.JwtIssuer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegisterHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void registersANewUserAndReturnsAToken() throws Exception {
        UserRepository repository = mock(UserRepository.class);
        JwtIssuer jwtIssuer = mock(JwtIssuer.class);
        when(jwtIssuer.issue(anyString(), anyString())).thenReturn("signed.jwt.token");

        RegisterHandler handler = new RegisterHandler(repository, jwtIssuer);
        Map<String, Object> input = Map.of("body", "{\"email\":\"alice@example.com\",\"password\":\"password123\"}");

        Map<String, Object> result = handler.handleRequest(input, new NoOpContext());

        assertEquals(201, result.get("statusCode"));
        Map<?, ?> body = MAPPER.readValue((String) result.get("body"), Map.class);
        assertEquals("alice@example.com", body.get("email"));
        assertEquals("signed.jwt.token", body.get("token"));
    }

    @Test
    void rejectsADuplicateEmailWith409() throws Exception {
        UserRepository repository = mock(UserRepository.class);
        JwtIssuer jwtIssuer = mock(JwtIssuer.class);
        doThrow(ConditionalCheckFailedException.builder().build()).when(repository).createUser(any());

        RegisterHandler handler = new RegisterHandler(repository, jwtIssuer);
        Map<String, Object> input = Map.of("body", "{\"email\":\"alice@example.com\",\"password\":\"password123\"}");

        Map<String, Object> result = handler.handleRequest(input, new NoOpContext());

        assertEquals(409, result.get("statusCode"));
    }

    @Test
    void rejectsAShortPasswordWith400() {
        UserRepository repository = mock(UserRepository.class);
        JwtIssuer jwtIssuer = mock(JwtIssuer.class);

        RegisterHandler handler = new RegisterHandler(repository, jwtIssuer);
        Map<String, Object> input = Map.of("body", "{\"email\":\"alice@example.com\",\"password\":\"short\"}");

        Map<String, Object> result = handler.handleRequest(input, new NoOpContext());

        assertEquals(400, result.get("statusCode"));
    }

    static class NoOpContext implements Context {
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

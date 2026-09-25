package com.cloudcart.auth.handler;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.cloudcart.auth.model.User;
import com.cloudcart.auth.repository.UserRepository;
import com.cloudcart.auth.util.JwtIssuer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void logsInWithCorrectCredentials() throws Exception {
        User user = new User();
        user.setEmail("alice@example.com");
        user.setUserId("user-123");
        user.setPasswordHash(BCrypt.hashpw("password123", BCrypt.gensalt()));

        UserRepository repository = mock(UserRepository.class);
        when(repository.getByEmail("alice@example.com")).thenReturn(user);
        JwtIssuer jwtIssuer = mock(JwtIssuer.class);
        when(jwtIssuer.issue(anyString(), anyString())).thenReturn("signed.jwt.token");

        LoginHandler handler = new LoginHandler(repository, jwtIssuer);
        Map<String, Object> input = Map.of("body", "{\"email\":\"alice@example.com\",\"password\":\"password123\"}");

        Map<String, Object> result = handler.handleRequest(input, new NoOpContext());

        assertEquals(200, result.get("statusCode"));
        Map<?, ?> body = MAPPER.readValue((String) result.get("body"), Map.class);
        assertEquals("user-123", body.get("userId"));
        assertEquals("signed.jwt.token", body.get("token"));
    }

    @Test
    void rejectsAWrongPasswordWith401() {
        User user = new User();
        user.setEmail("alice@example.com");
        user.setUserId("user-123");
        user.setPasswordHash(BCrypt.hashpw("password123", BCrypt.gensalt()));

        UserRepository repository = mock(UserRepository.class);
        when(repository.getByEmail("alice@example.com")).thenReturn(user);
        JwtIssuer jwtIssuer = mock(JwtIssuer.class);

        LoginHandler handler = new LoginHandler(repository, jwtIssuer);
        Map<String, Object> input = Map.of("body", "{\"email\":\"alice@example.com\",\"password\":\"wrong-password\"}");

        Map<String, Object> result = handler.handleRequest(input, new NoOpContext());

        assertEquals(401, result.get("statusCode"));
    }

    @Test
    void rejectsAnUnknownEmailWith401() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.getByEmail(anyString())).thenReturn(null);
        JwtIssuer jwtIssuer = mock(JwtIssuer.class);

        LoginHandler handler = new LoginHandler(repository, jwtIssuer);
        Map<String, Object> input = Map.of("body", "{\"email\":\"nobody@example.com\",\"password\":\"password123\"}");

        Map<String, Object> result = handler.handleRequest(input, new NoOpContext());

        assertEquals(401, result.get("statusCode"));
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

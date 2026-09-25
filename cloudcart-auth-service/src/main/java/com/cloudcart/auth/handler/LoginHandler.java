package com.cloudcart.auth.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudcart.auth.model.User;
import com.cloudcart.auth.repository.DynamoUserRepository;
import com.cloudcart.auth.repository.UserRepository;
import com.cloudcart.auth.util.JwtIssuer;
import com.cloudcart.auth.util.NimbusJwtIssuer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Map;

public class LoginHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UserRepository repository;
    private final JwtIssuer jwtIssuer;

    public LoginHandler() {
        this(new DynamoUserRepository(), new NimbusJwtIssuer(System.getenv("JWT_SECRET")));
    }

    LoginHandler(UserRepository repository, JwtIssuer jwtIssuer) {
        this.repository = repository;
        this.jwtIssuer = jwtIssuer;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            String body = (String) input.get("body");
            Map<String, Object> requestBody = MAPPER.readValue(body, new TypeReference<>() {});

            String email = (String) requestBody.get("email");
            String password = (String) requestBody.get("password");
            if (email == null || email.isBlank() || password == null || password.isBlank()) {
                return response(400, "{\"error\":\"email and password are required\"}");
            }
            email = email.trim().toLowerCase();

            User user = repository.getByEmail(email);
            if (user == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
                return response(401, "{\"error\":\"Invalid email or password\"}");
            }

            String token = jwtIssuer.issue(user.getUserId(), user.getEmail());
            String responseBody = MAPPER.writeValueAsString(
                    Map.of("token", token, "userId", user.getUserId(), "email", user.getEmail()));
            return response(200, responseBody);
        } catch (Exception e) {
            context.getLogger().log("LoginHandler error: " + e.getMessage());
            return response(500, "{\"error\":\"Failed to log in\"}");
        }
    }

    private Map<String, Object> response(int statusCode, String body) {
        return Map.of(
                "statusCode", statusCode,
                "headers", Map.of("Content-Type", "application/json"),
                "body", body
        );
    }
}

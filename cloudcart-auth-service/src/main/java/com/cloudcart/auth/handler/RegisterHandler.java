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
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;

public class RegisterHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UserRepository repository;
    private final JwtIssuer jwtIssuer;

    public RegisterHandler() {
        this(new DynamoUserRepository(), new NimbusJwtIssuer(System.getenv("JWT_SECRET")));
    }

    RegisterHandler(UserRepository repository, JwtIssuer jwtIssuer) {
        this.repository = repository;
        this.jwtIssuer = jwtIssuer;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            String body = (String) input.get("body");
            Map<String, Object> requestBody = MAPPER.readValue(body, new TypeReference<>() {});

            String email = normalizeEmail((String) requestBody.get("email"));
            String password = (String) requestBody.get("password");

            List<String> errors = new ArrayList<>();
            if (email == null || email.isBlank() || !email.contains("@")) {
                errors.add("A valid email is required");
            }
            if (password == null || password.length() < 8) {
                errors.add("password must be at least 8 characters");
            }
            if (!errors.isEmpty()) {
                return response(400, MAPPER.writeValueAsString(Map.of("error", "Validation failed", "details", errors)));
            }

            String userId = UUID.randomUUID().toString();
            User user = new User();
            user.setEmail(email);
            user.setUserId(userId);
            user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
            user.setCreatedAt(Instant.now().toString());

            try {
                repository.createUser(user);
            } catch (ConditionalCheckFailedException e) {
                return response(409, "{\"error\":\"Email is already registered\"}");
            }

            String token = jwtIssuer.issue(userId, email);
            String responseBody = MAPPER.writeValueAsString(Map.of("token", token, "userId", userId, "email", email));
            return response(201, responseBody);
        } catch (Exception e) {
            context.getLogger().log("RegisterHandler error: " + e.getMessage());
            return response(500, "{\"error\":\"Failed to register\"}");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private Map<String, Object> response(int statusCode, String body) {
        return Map.of(
                "statusCode", statusCode,
                "headers", Map.of("Content-Type", "application/json"),
                "body", body
        );
    }
}

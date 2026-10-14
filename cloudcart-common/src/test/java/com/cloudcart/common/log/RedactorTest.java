package com.cloudcart.common.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedactorTest {

    @AfterEach
    void reset() {
        LogConfig.setEnv(null);
    }

    @Test
    void masksPasswordInJsonBody() {
        String out = Redactor.body("{\"email\":\"a@b.com\",\"password\":\"Hunter2!\"}");
        assertTrue(out.contains("\"password\":\"***\""), out);
        assertTrue(out.contains("a@b.com"));
        assertFalse(out.contains("Hunter2"));
    }

    @Test
    void masksAuthTokenInsideNestedArraysButKeepsTokenUsageCounters() {
        String out = Redactor.body("{\"usage\":{\"prompt_tokens\":12,\"totalTokens\":30},"
                + "\"calls\":[{\"name\":\"view_cart\",\"authToken\":\"abc.def\"}]}");
        assertTrue(out.contains("\"authToken\":\"***\""), out);
        assertTrue(out.contains("\"prompt_tokens\":12"), out);
        assertTrue(out.contains("\"totalTokens\":30"), out);
    }

    @Test
    void masksBearerTokensJwtsAndGroqKeysInsideFreeText() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJl";
        String out = Redactor.body("{\"content\":\"my token is " + jwt + " and key gsk_abcdefghijklmnop1234, hdr Bearer abc.def-ghi\"}");
        assertFalse(out.contains("eyJ"), out);
        assertFalse(out.contains("gsk_abcdef"), out);
        assertFalse(out.contains("abc.def-ghi"), out);
        assertTrue(out.contains("Bearer ***"), out);
    }

    @Test
    void scrubsNonJsonAndTruncatedJsonAsText() {
        assertEquals("plain Bearer ***", Redactor.body("plain Bearer abc123"));
        String broken = Redactor.body("{\"password\":\"x\", Bearer tok.en");
        assertTrue(broken.contains("Bearer ***"), broken);
    }

    @Test
    void masksSensitiveHeadersCaseInsensitivelyAndKeepsOthers() {
        Map<String, Object> out = Redactor.headers(Map.of(
                "Authorization", "Bearer abc", "x-api-key", "k", "Content-Type", "application/json"));
        assertEquals("***", out.get("Authorization"));
        assertEquals("***", out.get("x-api-key"));
        assertEquals("application/json", out.get("Content-Type"));
    }

    @Test
    void truncatesLongBodiesAndReportsHowMuchWasCut() {
        LogConfig.setEnv(k -> "LOG_MAX_BODY_CHARS".equals(k) ? "10" : null);
        String out = Redactor.body("x".repeat(25));
        assertTrue(out.startsWith("xxxxxxxxxx…[truncated 15 chars]"), out);
    }

    @Test
    void bodiesAreSuppressedWhenLogBodiesIsFalse() {
        LogConfig.setEnv(k -> "LOG_BODIES".equals(k) ? "false" : null);
        assertNull(Redactor.body("{\"a\":1}"));
        assertNull(Redactor.body(Map.of("a", 1)));
    }

    @Test
    void serializesObjectsBeforeRedacting() {
        String out = Redactor.body(Map.of("password", "p", "ok", "v"));
        assertTrue(out.contains("\"password\":\"***\""), out);
        assertTrue(out.contains("\"ok\":\"v\""), out);
    }
}

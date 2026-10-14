package com.cloudcart.common.log;

import java.util.function.Function;

/**
 * Logging switches, read from the environment so they can be changed per deploy:
 * <ul>
 *   <li>{@code LOG_LEVEL} - DEBUG, INFO (default), WARN or ERROR</li>
 *   <li>{@code LOG_BODIES} - "false" logs metadata only; default true (dev stacks)</li>
 *   <li>{@code LOG_MAX_BODY_CHARS} - per-body cap before truncation, default 4000</li>
 * </ul>
 */
public final class LogConfig {

    public static final int DEFAULT_MAX_BODY_CHARS = 4000;

    private static volatile Function<String, String> env = System::getenv;

    private LogConfig() {}

    /** Test hook: replace the environment lookup. Pass null to restore the real environment. */
    public static void setEnv(Function<String, String> lookup) {
        env = lookup == null ? System::getenv : lookup;
    }

    public static Level level() {
        return Level.parse(env.apply("LOG_LEVEL"));
    }

    public static boolean logBodies() {
        String v = env.apply("LOG_BODIES");
        return v == null || !v.trim().equalsIgnoreCase("false");
    }

    public static int maxBodyChars() {
        String v = env.apply("LOG_MAX_BODY_CHARS");
        if (v == null || v.isBlank()) return DEFAULT_MAX_BODY_CHARS;
        try {
            int n = Integer.parseInt(v.trim());
            return n > 0 ? n : DEFAULT_MAX_BODY_CHARS;
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_BODY_CHARS;
        }
    }

    public enum Level {
        DEBUG, INFO, WARN, ERROR;

        static Level parse(String value) {
            if (value == null) return INFO;
            try {
                return valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return INFO;
            }
        }
    }
}

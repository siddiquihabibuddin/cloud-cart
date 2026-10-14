package com.cloudcart.agent;

import com.cloudcart.common.log.Correlation;
import com.cloudcart.common.log.JsonLogger;
import com.cloudcart.common.log.LogConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Test helper: captures JsonLogger output and resets shared logging state. */
public final class LogCapture {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final List<String> lines = new ArrayList<>();

    public static LogCapture start() {
        LogCapture c = new LogCapture();
        JsonLogger.setSink(c.lines::add);
        LogConfig.setEnv(null);
        Correlation.clear();
        return c;
    }

    public static void stop() {
        JsonLogger.setSink(null);
        LogConfig.setEnv(null);
        Correlation.clear();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> event(String name) {
        for (String line : lines) {
            try {
                Map<String, Object> m = MAPPER.readValue(line, Map.class);
                if (name.equals(m.get("event"))) return m;
            } catch (Exception e) {
                throw new AssertionError("not JSON: " + line, e);
            }
        }
        throw new AssertionError("no '" + name + "' event in: " + lines);
    }

    public boolean anyLineContains(String text) {
        return lines.stream().anyMatch(l -> l.contains(text));
    }
}

package com.cloudcart.common.log;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Captures JsonLogger output for assertions and resets global state between tests. */
public final class LogTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final List<String> lines = new ArrayList<>();

    public static LogTestSupport capture() {
        LogTestSupport s = new LogTestSupport();
        JsonLogger.setSink(s.lines::add);
        Correlation.clear();
        LogConfig.setEnv(null);
        return s;
    }

    public static void reset() {
        JsonLogger.setSink(null);
        LogConfig.setEnv(null);
        Correlation.clear();
    }

    public List<String> lines() {
        return lines;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> line(int index) {
        try {
            return MAPPER.readValue(lines.get(index), Map.class);
        } catch (Exception e) {
            throw new AssertionError("log line " + index + " is not JSON: " + lines.get(index), e);
        }
    }

    /** First logged line whose "event" equals the given name. */
    public Map<String, Object> event(String name) {
        for (int i = 0; i < lines.size(); i++) {
            Map<String, Object> m = line(i);
            if (name.equals(m.get("event"))) return m;
        }
        throw new AssertionError("no '" + name + "' event in: " + lines);
    }

    public boolean anyLineContains(String text) {
        return lines.stream().anyMatch(l -> l.contains(text));
    }
}

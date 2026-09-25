package com.cloudcart.agent.mcp;

import java.util.List;
import java.util.Map;

public interface ToolBridge {

    List<Map<String, Object>> listGroqTools();

    String callTool(String name, String argumentsJson) throws Exception;
}

package com.cloudcart.mcp.config;

import com.cloudcart.mcp.tool.CartTools;
import com.cloudcart.mcp.tool.OrderTools;
import com.cloudcart.mcp.tool.ProductTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class ToolConfig {

    @Bean
    ToolCallbackProvider cloudCartTools(ProductTools productTools, CartTools cartTools, OrderTools orderTools) {
        ToolCallbackProvider methods = MethodToolCallbackProvider.builder()
                .toolObjects(productTools, cartTools, orderTools)
                .build();
        // every tool call is logged (arguments, result, duration) with secrets redacted
        return ToolCallbackProvider.from(Arrays.stream(methods.getToolCallbacks())
                .map(LoggingToolCallback::new)
                .toList());
    }
}

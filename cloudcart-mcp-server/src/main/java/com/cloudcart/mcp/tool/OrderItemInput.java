package com.cloudcart.mcp.tool;

import org.springframework.ai.tool.annotation.ToolParam;

public record OrderItemInput(
        @ToolParam(description = "The product id") String productId,
        @ToolParam(description = "Quantity, minimum 1") int quantity,
        @ToolParam(description = "Unit price") double price) {
}

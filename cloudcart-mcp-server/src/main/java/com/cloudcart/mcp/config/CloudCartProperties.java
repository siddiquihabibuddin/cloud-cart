package com.cloudcart.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudcart")
public record CloudCartProperties(String unifiedApiUrl, String orderApiKey) {
}

package com.cloudcart.mcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class WebClientConfig {

    @Bean
    RestClient cloudCartRestClient(CloudCartProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.unifiedApiUrl())
                .build();
    }
}

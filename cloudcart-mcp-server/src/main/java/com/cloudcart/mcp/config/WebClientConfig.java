package com.cloudcart.mcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class WebClientConfig {

    @Bean
    RestClient cloudCartRestClient(CloudCartProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.unifiedApiUrl())
                // buffering lets the interceptor read the response body for logging and still
                // hand it on to the caller
                .requestFactory(new BufferingClientHttpRequestFactory(new JdkClientHttpRequestFactory()))
                .requestInterceptor(new LoggingRequestInterceptor("gateway"))
                .build();
    }
}

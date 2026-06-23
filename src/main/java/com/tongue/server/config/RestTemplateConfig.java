package com.tongue.server.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate tongueAgentRestTemplate(
            RestTemplateBuilder builder,
            AgentProperties agentProperties
    ) {
        return builder
                .setConnectTimeout(Duration.ofMillis(agentProperties.getConnectTimeoutMillis()))
                .setReadTimeout(Duration.ofMillis(agentProperties.getReadTimeoutMillis()))
                .build();
    }
}

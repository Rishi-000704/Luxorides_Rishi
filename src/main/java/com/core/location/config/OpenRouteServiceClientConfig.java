package com.core.location.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class OpenRouteServiceClientConfig {

    @Bean
    WebClient openRouteServiceWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.openrouteservice.org")
                .build();
    }
}

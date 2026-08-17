package com.core.location.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class GoogleMapsClientConfig {

    @Bean
    WebClient googleMapsWebClient() {
        return WebClient.builder()
                .baseUrl("https://maps.googleapis.com/maps/api")
                .build();
    }
}

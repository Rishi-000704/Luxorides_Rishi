package com.core.location.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class OpenRouteServiceClientConfig {

    // Reactor Netty is not on this project's classpath (WebClient here has
    // always resolved to Spring's JDK-HttpClient-backed connector) -- bound
    // the connection phase here; OpenRouteServiceGeoProvider applies the
    // response-side bound via a Mono#timeout, since JdkClientHttpConnector
    // has no connector-level read-timeout setter in this Spring Framework
    // version.
    @Bean
    WebClient openRouteServiceWebClient(@Value("${geo.http.connect-timeout-ms}") long connectTimeoutMs) {
        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        return WebClient.builder()
                .baseUrl("https://api.openrouteservice.org")
                .clientConnector(new JdkClientHttpConnector(jdkHttpClient))
                .build();
    }
}

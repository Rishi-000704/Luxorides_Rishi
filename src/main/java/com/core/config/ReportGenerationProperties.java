package com.core.config;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "report.generation")
public class ReportGenerationProperties {

    private Duration queuedTimeout = Duration.ofMinutes(2);
    private Duration processingTimeout = Duration.ofMinutes(10);
    private int maxAttempts = 3;
    private String timezone = "Asia/Kolkata";
    private int executorCorePoolSize = 2;
    private int executorMaxPoolSize = 4;
    private int executorQueueCapacity = 50;

    public ZoneId reportZone() {
        return ZoneId.of(timezone);
    }
}

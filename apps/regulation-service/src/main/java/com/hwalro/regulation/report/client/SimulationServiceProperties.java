package com.hwalro.regulation.report.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simulation-service")
public record SimulationServiceProperties(
        String baseUrl, Duration connectTimeout, Duration readTimeout, Integer maxResultCount) {}

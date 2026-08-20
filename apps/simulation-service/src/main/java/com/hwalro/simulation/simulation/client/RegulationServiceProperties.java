package com.hwalro.simulation.simulation.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "regulation-service")
public record RegulationServiceProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {}

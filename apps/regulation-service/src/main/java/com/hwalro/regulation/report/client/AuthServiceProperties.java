package com.hwalro.regulation.report.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth-service")
public record AuthServiceProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {}

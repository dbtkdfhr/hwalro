package com.hwalro.simulation.simulation.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RegulationUsageClient {
    private final RestClient restClient;

    public RegulationUsageClient(RegulationServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        if (properties.connectTimeout() != null) {
            requestFactory.setConnectTimeout(properties.connectTimeout());
        }
        if (properties.readTimeout() != null) {
            requestFactory.setReadTimeout(properties.readTimeout());
        }
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl() != null ? properties.baseUrl() : "http://localhost:8082")
                .requestFactory(requestFactory)
                .build();
    }

    public RegulationUsageResponse checkUsage(Long simulationResultId, String authorization) {
        try {
            RegulationUsageResponse response = restClient
                    .get()
                    .uri("/api/regulations/simulation-usage/{simulationResultId}", simulationResultId)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(RegulationUsageResponse.class);
            return response != null ? response : new RegulationUsageResponse(false);
        } catch (RestClientException exception) {
            throw new IllegalStateException("규정 서비스 연동 중 오류가 발생했습니다.", exception);
        }
    }

    public record RegulationUsageResponse(boolean usedInReports) {}
}

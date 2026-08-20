package com.hwalro.regulation.report.client;

import com.hwalro.regulation.report.dto.ReportVisualContextResponse;
import com.hwalro.regulation.report.exception.SimulationServiceException;
import com.hwalro.regulation.report.exception.SimulationServiceTimeoutException;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class SimulationReportVisualContextClient {
    private final RestClient restClient;

    public SimulationReportVisualContextClient(SimulationServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public List<ReportVisualContextResponse> findAll(List<Long> simulationResultIds, String authorization) {
        try {
            List<ReportVisualContextResponse> contexts = restClient
                    .post()
                    .uri("/api/simulation-results/report-visual-contexts")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .body(new ContextRequest(simulationResultIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return contexts == null ? List.of() : contexts;
        } catch (ResourceAccessException exception) {
            throw new SimulationServiceTimeoutException("시뮬레이션 미니맵 조회 시간이 초과되었습니다.", exception);
        } catch (RestClientException exception) {
            throw new SimulationServiceException("시뮬레이션 미니맵을 조회할 수 없습니다.", exception);
        }
    }

    private record ContextRequest(List<Long> simulationResultIds) {}
}

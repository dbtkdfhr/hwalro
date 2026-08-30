package com.hwalro.regulation.report.client;

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
public class SimulationReportContextClient {
    private final RestClient restClient;

    public SimulationReportContextClient(SimulationServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public List<Context> findAll(List<Long> simulationResultIds, String authorization) {
        try {
            List<Context> contexts = restClient
                    .post()
                    .uri("/api/simulation-results/report-contexts")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .body(new ContextRequest(simulationResultIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return contexts == null ? List.of() : contexts;
        } catch (ResourceAccessException exception) {
            throw new SimulationServiceTimeoutException("시뮬레이션 결과 조회 시간이 초과되었습니다.", exception);
        } catch (RestClientException exception) {
            throw new SimulationServiceException("시뮬레이션 결과를 조회할 수 없습니다.", exception);
        }
    }

    private record ContextRequest(List<Long> simulationResultIds) {}

    public record Context(
            Long simulationResultId,
            Long simulationId,
            Long layoutId,
            Long layoutVersionId,
            String layoutTitle,
            List<Metric> metrics,
            List<Bottleneck> bottlenecks) {
        public Context(
                Long simulationResultId,
                Long simulationId,
                Long layoutId,
                String layoutTitle,
                List<Metric> metrics,
                List<Bottleneck> bottlenecks) {
            this(simulationResultId, simulationId, layoutId, null, layoutTitle, metrics, bottlenecks);
        }
    }

    public record Metric(String metricType, double metricValue, String unit) {}

    public record Bottleneck(
            int order, double startTimeSeconds, double endTimeSeconds, double peakDensity, double thresholdValue) {}
}

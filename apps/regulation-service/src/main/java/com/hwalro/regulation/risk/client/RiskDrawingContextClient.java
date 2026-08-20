package com.hwalro.regulation.risk.client;

import com.hwalro.regulation.report.client.SimulationServiceProperties;
import com.hwalro.regulation.report.exception.SimulationServiceException;
import com.hwalro.regulation.report.exception.SimulationServiceTimeoutException;
import com.hwalro.regulation.risk.dto.RiskDrawingContextResponse;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RiskDrawingContextClient {
    private static final int DEFAULT_MAX_RESULT_COUNT = 6;

    private final RestClient restClient;
    private final int maxResultCount;

    public RiskDrawingContextClient(SimulationServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.maxResultCount =
                properties.maxResultCount() == null ? DEFAULT_MAX_RESULT_COUNT : properties.maxResultCount();
    }

    public int maxResultCount() {
        return maxResultCount;
    }

    public RiskDrawingContextResponse findOne(Long simulationResultId, String authorization) {
        List<RiskDrawingContextResponse> contexts = findAll(List.of(simulationResultId), authorization);
        return contexts.isEmpty() ? null : contexts.get(0);
    }

    public List<RiskDrawingContextResponse> findAll(List<Long> simulationResultIds, String authorization) {
        try {
            List<RiskDrawingContextResponse> contexts = restClient
                    .post()
                    .uri("/api/simulation-results/drawing-contexts")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .body(new DrawingContextRequest(simulationResultIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return contexts == null ? List.of() : contexts;
        } catch (ResourceAccessException exception) {
            throw new SimulationServiceTimeoutException("시뮬레이션 도면 조회 시간이 초과되었습니다.", exception);
        } catch (RestClientException exception) {
            throw new SimulationServiceException("시뮬레이션 도면을 조회할 수 없습니다.", exception);
        }
    }

    private record DrawingContextRequest(List<Long> simulationResultIds) {}
}

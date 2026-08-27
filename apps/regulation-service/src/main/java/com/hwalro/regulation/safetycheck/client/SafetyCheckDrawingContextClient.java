package com.hwalro.regulation.safetycheck.client;

import com.hwalro.regulation.report.client.SimulationServiceProperties;
import com.hwalro.regulation.report.exception.SimulationServiceException;
import com.hwalro.regulation.report.exception.SimulationServiceTimeoutException;
import com.hwalro.regulation.risk.dto.LayoutDrawingContextResponse;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class SafetyCheckDrawingContextClient {
    private final RestClient restClient;

    public SafetyCheckDrawingContextClient(SimulationServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public List<LayoutDrawingContextResponse> findLayoutContexts(List<Long> layoutIds, String authorization) {
        try {
            List<LayoutDrawingContextResponse> contexts = restClient
                    .post()
                    .uri("/api/drawings/drawing-contexts")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .body(new LayoutDrawingContextsRequest(layoutIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return contexts == null ? List.of() : contexts;
        } catch (ResourceAccessException exception) {
            throw new SimulationServiceTimeoutException("도면 조회 시간이 초과되었습니다.", exception);
        } catch (RestClientException exception) {
            throw new SimulationServiceException("도면을 조회할 수 없습니다.", exception);
        }
    }

    private record LayoutDrawingContextsRequest(List<Long> layoutIds) {}
}

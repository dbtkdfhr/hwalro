package com.hwalro.regulation.law.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.hwalro.regulation.law.exception.LawApiConfigurationException;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
/** 국가법령정보센터 호출에 필요한 인증·URL·파라미터를 한곳에 모은 어댑터이다. */
public class LawApiClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private final LawApiProperties properties;
    private final RestClient restClient;

    public LawApiClient(LawApiProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 현재 시행 중인 법령만 검색한다.
     *
     * <p>{@code nw=3}은 현행 법령만 요청하는 국가법령정보센터 목록 API 파라미터다.
     */
    public JsonNode searchCurrentLaws(String query, int page, int size) {
        requireAuthenticationValue();
        return restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/DRF/lawSearch.do")
                        .queryParam("OC", properties.oc())
                        .queryParam("target", "eflaw")
                        .queryParam("type", "JSON")
                        .queryParam("nw", "3")
                        .queryParam("search", "1")
                        .queryParam("query", query)
                        .queryParam("display", size)
                        .queryParam("page", page)
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    /**
     * 목록 API가 반환한 법령일련번호(MST)로 현행 법령 본문을 조회한다.
     *
     * <p>상세 조회에는 목록 전용 파라미터인 {@code nw}를 보내지 않는다.
     */
    public JsonNode getCurrentLaw(String serialNumber) {
        return getCurrentLaw("MST", serialNumber);
    }

    /** 국가법령정보센터의 법령 ID로 현행 법령 본문을 조회한다. */
    public JsonNode getCurrentLawById(String lawId) {
        return getCurrentLaw("ID", lawId);
    }

    /** 선택 법령과 국가법령정보센터가 공식적으로 연결한 법령 목록을 조회한다. */
    public JsonNode searchRelatedLaws(String lawId) {
        requireAuthenticationValue();
        return restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/DRF/lawSearch.do")
                        .queryParam("OC", properties.oc())
                        .queryParam("target", "lsRlt")
                        .queryParam("ID", lawId)
                        .queryParam("type", "JSON")
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode getCurrentLaw(String identifierName, String identifier) {
        requireAuthenticationValue();
        return restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/DRF/lawService.do")
                        .queryParam("OC", properties.oc())
                        .queryParam("target", "eflaw")
                        .queryParam(identifierName, identifier)
                        .queryParam("type", "JSON")
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    /** 인증값은 외부 API 요청 직전에만 확인해 애플리케이션 기동 자체는 막지 않는다. */
    private void requireAuthenticationValue() {
        if (!StringUtils.hasText(properties.oc())) {
            throw new LawApiConfigurationException("LAW_API_OC environment variable is required.");
        }
    }
}

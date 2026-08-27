package com.hwalro.simulation.zone.client;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * auth-service의 직원 디렉터리 계약을 소비한다. simulation-service는 사용자 ID만 저장하고 auth 데이터베이스를 직접 읽지 않는다.
 *
 * <p>실패 정책:
 *
 * <ul>
 *   <li><b>쓰기(배정)</b>: {@link #requireEmployee}가 502로 실패시켜 검증되지 않은 배정이 저장되지 않게 한다(fail-closed).
 *   <li><b>읽기(이름 표시)</b>: {@link #findAllQuietly}는 빈 목록을 돌려주어, 이름을 못 붙이더라도 화면 전체가 죽지 않게 한다.
 * </ul>
 */
@Component
public class EmployeeDirectoryClient {
    private static final Logger log = LoggerFactory.getLogger(EmployeeDirectoryClient.class);

    private final RestClient restClient;

    public EmployeeDirectoryClient(AuthServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public List<EmployeeSummary> findAll(String authorization) {
        try {
            List<EmployeeSummary> employees = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder.path("/api/auth/employees").build())
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return employees == null ? List.of() : employees;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "직원 정보를 조회할 수 없습니다.", exception);
        }
    }

    public List<EmployeeSummary> findAllQuietly(String authorization) {
        try {
            return findAll(authorization);
        } catch (ResponseStatusException exception) {
            log.warn("직원 디렉터리 조회에 실패해 이름 없이 응답합니다.");
            return List.of();
        }
    }

    /** 배정 대상이 활성 일반 직원인지 확인한다. 조회 자체가 실패하면 저장을 막기 위해 예외를 그대로 올린다. */
    public void requireEmployee(Long userId, String authorization) {
        if (userId == null) {
            return;
        }
        List<EmployeeSummary> found;
        try {
            found = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/auth/employees")
                            .queryParam("ids", userId)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<EmployeeSummary>>() {});
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "직원 정보를 확인할 수 없습니다.", exception);
        }
        boolean valid = found != null && found.stream().anyMatch(employee -> userId.equals(employee.id()));
        if (!valid) {
            throw new IllegalArgumentException("배정할 수 없는 사용자입니다: " + userId);
        }
    }

    public record EmployeeSummary(Long id, String name) {}
}

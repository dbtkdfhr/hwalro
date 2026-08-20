package com.hwalro.regulation.report.client;

import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthorDirectoryClient {
    private final RestClient restClient;

    public AuthorDirectoryClient(AuthServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public List<AuthorSummary> findByIds(List<Long> userIds, String authorization) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        try {
            List<AuthorSummary> authors = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/auth/users")
                            .queryParam("ids", userIds)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return authors == null ? List.of() : authors;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "작성자 정보를 조회할 수 없습니다.", exception);
        }
    }

    public record AuthorSummary(Long id, String name) {}
}

package com.hwalro.regulation.law.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodySpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;
import org.springframework.web.client.RestClient.ResponseSpec;

@SpringJUnitConfig(LawApiCacheTest.CacheTestConfig.class)
/** 실제 LawApiClient의 @Cacheable 프록시가 동일 요청을 캐싱하고 서로 다른 키는 별도 호출하는지 확인한다. */
class LawApiCacheTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ObjectNode VALID_RESPONSE = validResponse();

    private static ObjectNode validResponse() {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.set("LawSearch", OBJECT_MAPPER.createObjectNode());
        node.set("법령", OBJECT_MAPPER.createObjectNode());
        node.set("lsRltSearch", OBJECT_MAPPER.createObjectNode());
        return node;
    }

    @Autowired
    private LawApiClient lawApiClient;

    @Autowired
    private RestClient restClient;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private AtomicReference<JsonNode> lawApiBody;

    @Autowired
    private AtomicBoolean lawApiFailure;

    @BeforeEach
    void resetCacheAndInvocations() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        clearInvocations(restClient);
        lawApiBody.set(VALID_RESPONSE);
        lawApiFailure.set(false);
    }

    @Test
    void cachesRepeatedSearchByQueryAndPage() {
        JsonNode first = lawApiClient.searchCurrentLaws("소방", 1, 20);
        JsonNode second = lawApiClient.searchCurrentLaws("소방", 1, 20);

        assertThat(second).isSameAs(first);
        verify(restClient, times(1)).get();
    }

    @Test
    void fetchesSeparatelyForDifferentSearchKeys() {
        lawApiClient.searchCurrentLaws("소방", 1, 20);
        lawApiClient.searchCurrentLaws("피난", 1, 20);
        lawApiClient.searchCurrentLaws("소방", 2, 20);

        verify(restClient, times(3)).get();
    }

    @Test
    void cachesRepeatedDetailBySerialNumber() {
        JsonNode first = lawApiClient.getCurrentLaw("283705");
        JsonNode second = lawApiClient.getCurrentLaw("283705");

        assertThat(second).isSameAs(first);
        verify(restClient, times(1)).get();
    }

    @Test
    void keepsSerialNumberAndLawIdCachesSeparate() {
        lawApiClient.getCurrentLaw("283705");
        lawApiClient.getCurrentLawById("014189");

        verify(restClient, times(2)).get();
    }

    @Test
    void cachesRepeatedRelatedLawLookup() {
        lawApiClient.searchRelatedLaws("014189");
        lawApiClient.searchRelatedLaws("014189");

        verify(restClient, times(1)).get();
    }

    @Test
    void doesNotCacheNullResponses() {
        lawApiBody.set(null);

        lawApiClient.searchCurrentLaws("소방", 1, 20);
        lawApiClient.searchCurrentLaws("소방", 1, 20);

        verify(restClient, times(2)).get();
    }

    @Test
    void doesNotCacheErrorResponses() {
        lawApiBody.set(OBJECT_MAPPER.createObjectNode());

        lawApiClient.searchCurrentLaws("소방", 1, 20);
        lawApiClient.searchCurrentLaws("소방", 1, 20);

        verify(restClient, times(2)).get();
    }

    @Test
    void retriesAfterExternalApiFailure() {
        lawApiFailure.set(true);

        assertThatThrownBy(() -> lawApiClient.searchCurrentLaws("소방", 1, 20)).isInstanceOf(RuntimeException.class);
        JsonNode second = lawApiClient.searchCurrentLaws("소방", 1, 20);

        verify(restClient, times(2)).get();
        assertThat(second).isNotNull();
    }

    @Configuration
    @EnableCaching
    static class CacheTestConfig {
        @Bean
        LawApiProperties lawApiProperties() {
            return new LawApiProperties("https://www.law.go.kr", "test", List.of("소방"));
        }

        @Bean
        LawApiClient lawApiClient(LawApiProperties properties, RestClient.Builder builder) {
            return new LawApiClient(properties, builder);
        }

        @Bean
        RestClient.Builder restClientBuilder(RestClient restClient) {
            RestClient.Builder builder = mock(RestClient.Builder.class);
            doReturn(builder).when(builder).baseUrl(anyString());
            doReturn(builder).when(builder).requestFactory(any());
            doReturn(restClient).when(builder).build();
            return builder;
        }

        @Bean
        AtomicReference<JsonNode> lawApiBody() {
            return new AtomicReference<>(VALID_RESPONSE);
        }

        @Bean
        AtomicBoolean lawApiFailure() {
            return new AtomicBoolean(false);
        }

        @Bean
        RestClient restClient(AtomicReference<JsonNode> lawApiBody, AtomicBoolean lawApiFailure) {
            ResponseSpec responseSpec = mock(ResponseSpec.class);
            doAnswer(invocation -> {
                        if (lawApiFailure.getAndSet(false)) {
                            throw new RuntimeException("external law api failure");
                        }
                        return lawApiBody.get();
                    })
                    .when(responseSpec)
                    .body(JsonNode.class);

            RequestBodySpec bodySpec = mock(RequestBodySpec.class);
            doReturn(responseSpec).when(bodySpec).retrieve();

            RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
            doReturn(bodySpec).when(uriSpec).uri(any(Function.class));

            RestClient restClient = mock(RestClient.class);
            doReturn(uriSpec).when(restClient).get();
            return restClient;
        }

        @Bean
        CacheManager cacheManager() {
            CaffeineCacheManager cacheManager = new CaffeineCacheManager();
            cacheManager.setCaffeine(Caffeine.newBuilder().maximumSize(10).expireAfterWrite(Duration.ofHours(1)));
            return cacheManager;
        }
    }
}

package com.hwalro.regulation.law.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.hwalro.regulation.law.api.LawApiClient;
import com.hwalro.regulation.law.api.LawApiProperties;
import com.hwalro.regulation.law.dto.RegulationArticle;
import com.hwalro.regulation.law.dto.RegulationDetail;
import com.hwalro.regulation.law.dto.RegulationSearchResponse;
import com.hwalro.regulation.law.dto.RegulationSummary;
import com.hwalro.regulation.law.dto.RelatedRegulation;
import com.hwalro.regulation.law.exception.RegulationNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
/** 외부 법령 응답의 한글 필드를 프론트엔드 전용 응답으로 변환한다. */
public class RegulationService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PAGE = 100_000;

    private final LawApiClient lawApiClient;
    private final LawApiProperties properties;
    private final Executor lawApiExecutor;

    @Autowired
    public RegulationService(
            LawApiClient lawApiClient,
            LawApiProperties properties,
            @Qualifier("lawApiExecutor") Executor lawApiExecutor) {
        this.lawApiClient = lawApiClient;
        this.properties = properties;
        this.lawApiExecutor = lawApiExecutor;
    }

    RegulationService(LawApiClient lawApiClient, LawApiProperties properties) {
        this(lawApiClient, properties, Runnable::run);
    }

    /** 검색어가 있으면 단일 검색 결과를, 없으면 안전 분야 기본 목록을 반환한다. */
    public RegulationSearchResponse search(String query, int page, int size) {
        validatePage(page, size);

        if (StringUtils.hasText(query)) {
            JsonNode response = lawApiClient.searchCurrentLaws(query.trim(), page, size);
            return toSearchResponse(response, page, size);
        }

        return searchDefaultSafetyLaws(page, size);
    }

    /** 선택한 법령의 기본 정보와 표시 가능한 조문 텍스트를 반환한다. */
    public RegulationDetail getDetail(String serialNumber) {
        if (!StringUtils.hasText(serialNumber)) {
            throw new IllegalArgumentException("serialNumber must not be blank.");
        }

        return toDetail(lawApiClient.getCurrentLaw(serialNumber), serialNumber);
    }

    /** 관련 법령 카드가 가진 법령 ID로 같은 상세 화면을 다시 연다. */
    public RegulationDetail getDetailByLawId(String lawId) {
        if (!StringUtils.hasText(lawId)) {
            throw new IllegalArgumentException("lawId must not be blank.");
        }

        return toDetail(lawApiClient.getCurrentLawById(lawId), "");
    }

    /** 국가법령정보센터의 공식 법령 간 관계를 카드 표시용 데이터로 변환한다. */
    public List<RelatedRegulation> getRelatedLaws(String lawId) {
        if (!StringUtils.hasText(lawId)) {
            throw new IllegalArgumentException("lawId must not be blank.");
        }

        List<RelatedRegulation> relatedLaws = new ArrayList<>();
        JsonNode response = lawApiClient.searchRelatedLaws(lawId);
        forEachLaw(
                response.path("lsRltSearch").path("법령").path("관련법령"),
                relatedLaw -> relatedLaws.add(new RelatedRegulation(
                        text(relatedLaw, "관련법령ID"),
                        text(relatedLaw, "관련법령명"),
                        text(relatedLaw, "법령간관계"),
                        text(relatedLaw, "관련법령본문조회"))));
        return relatedLaws;
    }

    private RegulationDetail toDetail(JsonNode response, String serialNumber) {
        JsonNode law = response.path("법령");
        if (law.isMissingNode() || law.isEmpty()) {
            throw new RegulationNotFoundException(serialNumber);
        }

        JsonNode basicInfo = law.path("기본정보");
        return new RegulationDetail(
                serialNumber,
                text(basicInfo, "법령ID"),
                text(basicInfo, "법령명_한글"),
                text(basicInfo.path("법종구분"), "content"),
                text(basicInfo.path("소관부처"), "content"),
                text(basicInfo, "공포일자"),
                text(basicInfo, "시행일자"),
                articles(law.path("조문").path("조문단위")));
    }

    // P2: 주의 항목과 법령 조문을 연결하는 기능은 별도 API와 데이터 모델로 구현한다.

    /** 검색 전 화면에 보여줄 안전 관련 법령 후보를 키워드별 결과에서 만든다. */
    private RegulationSearchResponse searchDefaultSafetyLaws(int page, int size) {
        Map<String, RegulationSummary> uniqueLaws = new LinkedHashMap<>();
        // ponytail: 기본 키워드만 병렬 조회한다. 응답량 또는 호출 수가 커지면 캐시를 추가한다.
        List<CompletableFuture<List<RegulationSummary>>> searches = properties.defaultKeywords().stream()
                .map(keyword -> CompletableFuture.supplyAsync(() -> searchSummaries(keyword), lawApiExecutor))
                .toList();
        for (CompletableFuture<List<RegulationSummary>> search : searches) {
            for (RegulationSummary summary : search.join()) {
                uniqueLaws.putIfAbsent(summary.serialNumber(), summary);
            }
        }

        List<RegulationSummary> laws = new ArrayList<>(uniqueLaws.values());
        laws.sort(Comparator.comparing(RegulationSummary::name));
        int start = Math.min((page - 1) * size, laws.size());
        int end = Math.min(start + size, laws.size());
        return new RegulationSearchResponse(laws.size(), page, size, end < laws.size(), laws.subList(start, end));
    }

    private List<RegulationSummary> searchSummaries(String keyword) {
        JsonNode response = lawApiClient.searchCurrentLaws(keyword, 1, MAX_PAGE_SIZE);
        List<RegulationSummary> summaries = new ArrayList<>();
        forEachLaw(response.path("LawSearch").path("law"), law -> summaries.add(toSummary(law)));
        return summaries;
    }

    /** 국가법령정보센터의 목록 응답을 서비스 목록 DTO로 축소한다. */
    private RegulationSearchResponse toSearchResponse(JsonNode response, int page, int size) {
        JsonNode searchResult = response.path("LawSearch");
        List<RegulationSummary> laws = new ArrayList<>();
        forEachLaw(searchResult.path("law"), law -> laws.add(toSummary(law)));
        int totalCount = searchResult.path("totalCnt").asInt(laws.size());
        return new RegulationSearchResponse(totalCount, page, size, page * size < totalCount, laws);
    }

    /** 목록 화면에 필요한 필드만 추출해 외부 API의 원본 구조를 노출하지 않는다. */
    private RegulationSummary toSummary(JsonNode law) {
        return new RegulationSummary(
                text(law, "법령일련번호"),
                text(law, "법령ID"),
                text(law, "법령명한글"),
                text(law, "법령구분명"),
                text(law, "소관부처명"),
                text(law, "시행일자"));
    }

    /** 장·절 제목과 조문을 같은 배열로 반환하고, 제목이 없는 항목은 구분선으로 표시하도록 표시한다. */
    private List<RegulationArticle> articles(JsonNode articleNodes) {
        List<RegulationArticle> articles = new ArrayList<>();
        forEachLaw(articleNodes, article -> {
            String number = text(article, "조문번호");
            String title = text(article, "조문제목");
            String content = collectContent(article);
            articles.add(new RegulationArticle(
                    number, title, content, text(article, "조문시행일자"), !StringUtils.hasText(number)));
        });
        return articles;
    }

    /** 조문 아래의 항·호·목 텍스트를 화면에서 읽을 수 있도록 하나의 문자열로 합친다. */
    private String collectContent(JsonNode node) {
        List<String> values = new ArrayList<>();
        String articleContent = text(node, "조문내용");
        if (StringUtils.hasText(articleContent)) {
            values.add(articleContent);
        }
        node.fields().forEachRemaining(field -> {
            if (!field.getKey().equals("조문내용")) {
                collectNestedContent(field.getValue(), values);
            }
        });
        return String.join("\n", values);
    }

    /** 항·호·목의 원래 중첩 순서를 유지하며 `내용` 텍스트만 뒤에 추가한다. */
    private void collectNestedContent(JsonNode node, List<String> values) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                if (field.getKey().endsWith("내용") && field.getValue().isTextual()) {
                    values.add(field.getValue().asText());
                } else {
                    collectNestedContent(field.getValue(), values);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectNestedContent(child, values));
        }
    }

    /** 외부 API가 단일 객체 또는 배열을 반환하는 차이를 호출부에서 숨긴다. */
    private void forEachLaw(JsonNode node, Consumer<JsonNode> consumer) {
        if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(consumer);
        } else if (node.isObject()) {
            consumer.accept(node);
        }
    }

    /** 후보 필드 중 비어 있지 않은 첫 값을 반환해 API 응답 명칭 차이를 흡수한다. */
    private String text(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = node.path(fieldName).asText();
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    /** 외부 API의 최대 목록 개수(100)를 넘는 요청을 서비스 경계에서 차단한다. */
    private void validatePage(int page, int size) {
        if (page < 1 || page > MAX_PAGE || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be between 1 and 100000 and size must be between 1 and 100.");
        }
    }
}

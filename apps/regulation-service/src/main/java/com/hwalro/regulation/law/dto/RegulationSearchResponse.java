package com.hwalro.regulation.law.dto;

import java.util.List;

/** 검색 결과와 목록 영역의 다음 페이지 여부를 반환한다. */
public record RegulationSearchResponse(
        int totalCount, int page, int size, boolean hasNext, List<RegulationSummary> items) {}

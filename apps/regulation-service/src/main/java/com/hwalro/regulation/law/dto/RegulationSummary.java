package com.hwalro.regulation.law.dto;

/** 좌측 검색 결과 목록에 표시하는 법령의 최소 정보다. */
public record RegulationSummary(
        String serialNumber,
        String lawId,
        String name,
        String lawType,
        String competentAuthority,
        String effectiveDate) {}

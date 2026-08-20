package com.hwalro.regulation.law.dto;

import java.util.List;

/** 선택된 법령의 메타데이터와 조문 목록을 함께 반환한다. */
public record RegulationDetail(
        String serialNumber,
        String lawId,
        String name,
        String lawType,
        String competentAuthority,
        String promulgationDate,
        String effectiveDate,
        List<RegulationArticle> articles) {}

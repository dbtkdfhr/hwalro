package com.hwalro.regulation.law.dto;

/** 우측 상세 화면에서 하나의 조문 또는 장·절 구분선을 표현한다. */
public record RegulationArticle(String number, String title, String content, String effectiveDate, boolean section) {}

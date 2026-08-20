package com.hwalro.regulation.law.api;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "law-api")
/** 국가법령정보센터 접속 정보와 검색 전 기본 목록에 쓸 안전 키워드 설정이다. */
public record LawApiProperties(String baseUrl, String oc, List<String> defaultKeywords) {}

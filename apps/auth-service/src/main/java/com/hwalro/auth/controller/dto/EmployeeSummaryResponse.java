package com.hwalro.auth.controller.dto;

/**
 * 구역 배정 대상 직원 요약. 다른 서비스가 소비하는 계약이므로 필요한 최소 정보만 노출한다. 로그인 ID·역할·활성 여부는 담지 않는다.
 */
public record EmployeeSummaryResponse(Long id, String name) {}

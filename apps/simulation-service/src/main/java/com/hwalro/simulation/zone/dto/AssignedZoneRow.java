package com.hwalro.simulation.zone.dto;

/** 직원에게 배정된 구역 한 건. 구역이 속한 도면까지 한 번에 읽어 목록 화면의 N+1을 피한다. */
public record AssignedZoneRow(
        Long zoneId,
        String zoneName,
        String zoneType,
        Long layoutId,
        String layoutTitle,
        Long layoutVersionId,
        Long defaultExitId,
        String defaultExitName) {}

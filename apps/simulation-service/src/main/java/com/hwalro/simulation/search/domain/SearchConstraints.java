package com.hwalro.simulation.search.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchConstraints(
        Map<Long, Double> moveRadii,
        List<ForbiddenZone> forbiddenZones,
        Map<Long, Boolean> rotationAllowed,
        Map<Long, Boolean> wallAnchored) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForbiddenZone(double x, double y, double width, double height) {}

    public static SearchConstraints empty() {
        return new SearchConstraints(Map.of(), List.of(), Map.of(), Map.of());
    }

    public String toJson(ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("제약 조건을 직렬화하지 못했습니다.", exception);
        }
    }

    public static SearchConstraints fromJson(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        try {
            return objectMapper.readValue(json, SearchConstraints.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 제약 조건을 읽지 못했습니다.", exception);
        }
    }
}

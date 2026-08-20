package com.hwalro.simulation.search.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchResult(String plannerVersion, List<SearchCandidate> candidates, List<RejectedCandidate> rejected) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchCandidate(
            String originFindingType,
            String operatorType,
            Long parentCandidateId,
            Double proxyScore,
            Double totalMoveDistance,
            List<ChangeOp> ops,
            JsonNode rationale) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RejectedCandidate(String operatorType, Long fabricId, String reason, List<ChangeOp> ops) {}
}

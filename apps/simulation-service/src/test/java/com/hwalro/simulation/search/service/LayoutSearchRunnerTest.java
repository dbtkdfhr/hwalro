package com.hwalro.simulation.search.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.search.domain.ChangeOp;
import com.hwalro.simulation.search.domain.SearchResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LayoutSearchRunnerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsSubQuantumSerializationNoiseWithoutTreatingItAsResize() {
        ChangeOp.FabricTransform before = transform("81.1115", "59.9083", "81.3000", "84.7483");
        ChangeOp.FabricTransform after =
                transform("9.905750000000005", "10.000000000000003", "10.094249999999995", "34.839999999999996");

        assertThatCode(() -> LayoutSearchRunner.validate(result(before, after), 1))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsChangedSpansEvenWhenRectangleAreaIsEqual() {
        ChangeOp.FabricTransform before = transform("0", "0", "2", "3");
        ChangeOp.FabricTransform after = transform("0", "0", "1", "6");

        assertThatThrownBy(() -> LayoutSearchRunner.validate(result(before, after), 1))
                .isInstanceOf(LayoutSearchRunner.SearchRunException.class)
                .hasMessageContaining("fabric 크기");
    }

    private SearchResult result(ChangeOp.FabricTransform before, ChangeOp.FabricTransform after) {
        ChangeOp operation = new ChangeOp("MOVE_FABRIC", 111L, before, after);
        SearchResult.SearchCandidate candidate = new SearchResult.SearchCandidate(
                "IDEAL_ROUTE", "BOUNDARY_DOCKING", null, 1.0, 1.0, List.of(operation), objectMapper.createObjectNode());
        return new SearchResult("IDEAL_ROUTE_DOCKING_V1", List.of(candidate), List.of());
    }

    private static ChangeOp.FabricTransform transform(String startX, String startY, String endX, String endY) {
        return new ChangeOp.FabricTransform(
                new BigDecimal(startX),
                new BigDecimal(startY),
                new BigDecimal(endX),
                new BigDecimal(endY),
                BigDecimal.ZERO);
    }
}

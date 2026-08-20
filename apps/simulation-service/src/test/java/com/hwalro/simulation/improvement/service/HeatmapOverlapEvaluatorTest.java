package com.hwalro.simulation.improvement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.improvement.domain.BottleneckArea;
import com.hwalro.simulation.improvement.domain.FabricChange;
import com.hwalro.simulation.improvement.domain.FabricState;
import com.hwalro.simulation.improvement.domain.HeatmapChunk;
import com.hwalro.simulation.improvement.domain.ImprovementSource;
import com.hwalro.simulation.improvement.domain.ProposalCandidate;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeatmapOverlapEvaluatorTest {
    private final HeatmapOverlapEvaluator evaluator = new HeatmapOverlapEvaluator(new ObjectMapper());

    @Test
    void usesCellCentersAndOnlyFramesWithinBottleneckTime() {
        FabricState fabric = new FabricState(1, RotatedRectangle.of(0, 0, 1, 1, 0));
        ProposalCandidate candidate = new ProposalCandidate(
                List.of(new FabricChange(1, fabric.bounds(), RotatedRectangle.of(2, 0, 3, 1, 0))));
        ImprovementSource source = new ImprovementSource(
                1,
                3,
                2,
                List.of(fabric),
                List.of(),
                List.of(),
                List.of(new BottleneckArea(RotatedRectangle.of(0, 0, 1, 1, 0), 0, 10)),
                List.of(new HeatmapChunk(0, heatmap())));

        assertEquals(1.0, evaluator.overlapDecrease(candidate, source));
    }

    private String heatmap() {
        return """
                {
                  "coordinateSystem":"FLOOR_PLAN",
                  "coordinateUnit":"METER",
                  "threshold":{"value":3.0,"unit":"PERSON_PER_M2"},
                  "grid":{"originX":0.0,"originY":0.0,"cellSize":1.0,"rows":2,"columns":3,"valueOrder":"ROW_MAJOR"},
                  "frames":[
                    {"timeSeconds":5.0,"values":[4.0,1.0,null,5.0,4.0,2.0]},
                    {"timeSeconds":20.0,"values":[10.0,1.0,null,5.0,4.0,2.0]}
                  ]
                }
                """;
    }
}

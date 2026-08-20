package com.hwalro.simulation.improvement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hwalro.simulation.improvement.domain.BottleneckArea;
import com.hwalro.simulation.improvement.domain.FabricState;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

class BottleneckFabricSelectorTest {
    private final BottleneckFabricSelector selector = new BottleneckFabricSelector();

    @Test
    void selectsOnlyFabricsWithinFiveMetersOfAnyBottleneck() {
        FabricState near = new FabricState(1L, RotatedRectangle.of(7, 0, 9, 2, 30));
        FabricState far = new FabricState(2L, RotatedRectangle.of(11, 0, 13, 2, 0));
        BottleneckArea bottleneck = new BottleneckArea(RotatedRectangle.of(0, 0, 2, 2, 0), 1, 5);

        assertEquals(List.of(near), selector.select(List.of(near, far), List.of(bottleneck)));
    }
}

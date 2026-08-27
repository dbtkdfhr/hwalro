package com.hwalro.simulation.zone.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hwalro.simulation.zone.domain.LayoutZone;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvacuationRouteCacheTest {
    @Test
    void cacheKeyIncludesRouteAlgorithmVersion() {
        LayoutZone zone = new LayoutZone();
        zone.setId(30L);
        zone.setX(BigDecimal.TEN);
        zone.setY(BigDecimal.valueOf(20));
        zone.setWidth(BigDecimal.valueOf(20));
        zone.setHeight(BigDecimal.TEN);
        zone.setDefaultExitId(910L);

        String key = EvacuationRouteCache.keyOf(803L, List.of(zone));

        assertThat(key).startsWith("natural-exit-approach-v3|");
    }
}

package com.hwalro.simulation.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LayoutSearchPrecisionTest {
    @Test
    void treatsOnlySubQuantumCoordinateNoiseAsEqual() {
        assertThat(LayoutSearchPrecision.same(new BigDecimal("1.0000000"), new BigDecimal("1.0000009")))
                .isTrue();
        assertThat(LayoutSearchPrecision.same(new BigDecimal("1.0000"), new BigDecimal("1.0001")))
                .isFalse();
    }

    @Test
    void canonicalizesCandidateIdentityToDatabaseCoordinateScale() {
        assertThat(LayoutSearchPrecision.key(new BigDecimal("9.905750000000005")))
                .isEqualTo("9.9058");
        assertThat(LayoutSearchPrecision.key(new BigDecimal("9.9058"))).isEqualTo("9.9058");
    }
}

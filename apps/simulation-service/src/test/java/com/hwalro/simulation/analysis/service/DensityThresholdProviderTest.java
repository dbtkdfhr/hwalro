package com.hwalro.simulation.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.analysis.mapper.BottleneckAnalysisMapper;
import com.hwalro.simulation.analysis.mapper.BottleneckAnalysisMapper.DensityThresholdRow;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DensityThresholdProviderTest {
    @Mock
    private BottleneckAnalysisMapper mapper;

    private DensityThresholdProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DensityThresholdProvider(mapper);
    }

    @Test
    void returnsCurrentSystemThreshold() {
        when(mapper.findDensityThreshold())
                .thenReturn(new DensityThresholdRow(new BigDecimal("3.500"), "PERSON_PER_M2"));

        var threshold = provider.getCurrent();

        assertThat(threshold.value()).isEqualByComparingTo("3.500");
        assertThat(threshold.unit()).isEqualTo("PERSON_PER_M2");
    }

    @Test
    void rejectsMissingSystemThreshold() {
        when(mapper.findDensityThreshold()).thenReturn(null);

        assertThatThrownBy(provider::getCurrent)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("밀집도 기준");
    }

    @Test
    void rejectsUnsupportedUnit() {
        when(mapper.findDensityThreshold()).thenReturn(new DensityThresholdRow(new BigDecimal("3.500"), "PEOPLE"));

        assertThatThrownBy(provider::getCurrent)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("단위");
    }

    @Test
    void rejectsNonPositiveThreshold() {
        when(mapper.findDensityThreshold()).thenReturn(new DensityThresholdRow(BigDecimal.ZERO, "PERSON_PER_M2"));

        assertThatThrownBy(provider::getCurrent)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("양수");
    }
}

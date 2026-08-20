package com.hwalro.simulation.analysis.service;

import com.hwalro.simulation.analysis.mapper.BottleneckAnalysisMapper;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class DensityThresholdProvider {
    public static final String DENSITY_UNIT = "PERSON_PER_M2";

    private final BottleneckAnalysisMapper mapper;

    public DensityThresholdProvider(BottleneckAnalysisMapper mapper) {
        this.mapper = mapper;
    }

    public DensityThreshold getCurrent() {
        BottleneckAnalysisMapper.DensityThresholdRow row = mapper.findDensityThreshold();
        if (row == null) {
            throw new IllegalStateException("시스템 공통 밀집도 기준이 설정되지 않았습니다.");
        }
        if (row.thresholdValue() == null || row.thresholdValue().signum() <= 0) {
            throw new IllegalStateException("시스템 공통 밀집도 기준은 양수여야 합니다.");
        }
        if (!DENSITY_UNIT.equals(row.unit())) {
            throw new IllegalStateException("지원하지 않는 밀집도 기준 단위입니다: " + row.unit());
        }
        return new DensityThreshold(row.thresholdValue(), row.unit());
    }

    public record DensityThreshold(BigDecimal value, String unit) {}
}

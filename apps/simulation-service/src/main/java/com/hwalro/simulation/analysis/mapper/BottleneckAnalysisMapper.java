package com.hwalro.simulation.analysis.mapper;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BottleneckAnalysisMapper {
    DensityThresholdRow findDensityThreshold();

    record DensityThresholdRow(BigDecimal thresholdValue, String unit) {}
}

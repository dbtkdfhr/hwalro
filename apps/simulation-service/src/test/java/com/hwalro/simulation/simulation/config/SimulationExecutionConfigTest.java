package com.hwalro.simulation.simulation.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimulationExecutionConfigTest {
    @Test
    void runsFourSimulationsAndQueuesTwentyMore() {
        var executor = new SimulationExecutionConfig().simulationExecutionExecutor();
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(4);
            assertThat(executor.getMaxPoolSize()).isEqualTo(4);
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity())
                    .isEqualTo(20);
        } finally {
            executor.shutdown();
        }
    }
}

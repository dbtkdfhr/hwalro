package com.hwalro.simulation.simulation.engine;

import static com.hwalro.simulation.simulation.config.SimulationExecutionConfig.EXECUTION_QUEUE_CAPACITY;
import static com.hwalro.simulation.simulation.config.SimulationExecutionConfig.MAX_CONCURRENT_EXECUTIONS;

import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;

@Component
public class EngineCapacity {
    private final Semaphore permits;

    public EngineCapacity() {
        this.permits = new Semaphore(MAX_CONCURRENT_EXECUTIONS + EXECUTION_QUEUE_CAPACITY, true);
    }

    public boolean tryAcquire() {
        return permits.tryAcquire();
    }

    public void acquire() {
        permits.acquireUninterruptibly();
    }

    public void release() {
        permits.release();
    }

    public int availablePermits() {
        return permits.availablePermits();
    }
}

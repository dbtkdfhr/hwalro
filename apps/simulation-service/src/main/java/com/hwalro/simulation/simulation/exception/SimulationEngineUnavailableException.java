package com.hwalro.simulation.simulation.exception;

public class SimulationEngineUnavailableException extends RuntimeException {
    public SimulationEngineUnavailableException(String message) {
        super(message);
    }

    public SimulationEngineUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

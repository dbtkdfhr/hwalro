package com.hwalro.regulation.report.exception;

public class SimulationServiceException extends RuntimeException {
    public SimulationServiceException(String message) {
        super(message);
    }

    public SimulationServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

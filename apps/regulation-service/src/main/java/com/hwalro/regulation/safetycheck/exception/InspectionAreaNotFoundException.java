package com.hwalro.regulation.safetycheck.exception;

public class InspectionAreaNotFoundException extends RuntimeException {
    public InspectionAreaNotFoundException(Long id) {
        super("Inspection area not found: " + id);
    }
}

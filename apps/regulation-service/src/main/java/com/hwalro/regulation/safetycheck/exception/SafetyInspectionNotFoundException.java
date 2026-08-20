package com.hwalro.regulation.safetycheck.exception;

public class SafetyInspectionNotFoundException extends RuntimeException {
    public SafetyInspectionNotFoundException(Long id) {
        super("Safety inspection not found: " + id);
    }
}

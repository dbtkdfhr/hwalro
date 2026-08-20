package com.hwalro.regulation.report.exception;

public class ReportDraftGenerationException extends RuntimeException {
    public ReportDraftGenerationException(String message) {
        super(message);
    }

    public ReportDraftGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

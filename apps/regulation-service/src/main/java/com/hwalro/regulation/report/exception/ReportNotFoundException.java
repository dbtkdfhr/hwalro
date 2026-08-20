package com.hwalro.regulation.report.exception;

public class ReportNotFoundException extends RuntimeException {
    public ReportNotFoundException(Long reportId) {
        super("보고서를 찾을 수 없습니다. id=" + reportId);
    }
}

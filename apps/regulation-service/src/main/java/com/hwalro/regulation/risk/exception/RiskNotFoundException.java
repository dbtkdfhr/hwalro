package com.hwalro.regulation.risk.exception;

public class RiskNotFoundException extends RuntimeException {
    public RiskNotFoundException(Long id) {
        super("주의 항목을 찾을 수 없습니다: " + id);
    }
}

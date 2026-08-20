package com.hwalro.regulation.law.exception;

/** 법령 API를 호출하는 데 필요한 로컬 환경 설정이 비어 있을 때 발생한다. */
public class LawApiConfigurationException extends RuntimeException {
    public LawApiConfigurationException(String message) {
        super(message);
    }
}

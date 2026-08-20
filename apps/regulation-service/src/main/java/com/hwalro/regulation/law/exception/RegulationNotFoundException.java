package com.hwalro.regulation.law.exception;

/** 존재하지 않거나 국가법령정보센터가 본문을 반환하지 않은 법령을 요청했을 때 발생한다. */
public class RegulationNotFoundException extends RuntimeException {
    public RegulationNotFoundException(String serialNumber) {
        super("Regulation not found: " + serialNumber);
    }
}

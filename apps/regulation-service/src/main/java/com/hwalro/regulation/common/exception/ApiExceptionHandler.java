package com.hwalro.regulation.common.exception;

import com.hwalro.regulation.common.jwt.ForbiddenException;
import com.hwalro.regulation.common.jwt.InvalidTokenException;
import com.hwalro.regulation.law.exception.LawApiConfigurationException;
import com.hwalro.regulation.law.exception.RegulationNotFoundException;
import com.hwalro.regulation.report.exception.InvalidReportDraftException;
import com.hwalro.regulation.report.exception.ReportDraftGenerationException;
import com.hwalro.regulation.report.exception.ReportNotFoundException;
import com.hwalro.regulation.report.exception.SimulationServiceException;
import com.hwalro.regulation.report.exception.SimulationServiceTimeoutException;
import com.hwalro.regulation.risk.exception.RiskNotFoundException;
import com.hwalro.regulation.safetycheck.exception.InspectionAreaNotFoundException;
import com.hwalro.regulation.safetycheck.exception.SafetyInspectionNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
/** 서비스 입력 오류와 외부 법령 API 오류를 프론트엔드가 구분할 수 있는 HTTP 상태로 변환한다. */
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    /** 페이지·크기·식별자 같은 클라이언트 입력값 오류를 반환한다. */
    public Map<String, String> handleBadRequest(IllegalArgumentException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return Map.of("message", "잘못된 요청 값입니다.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return Map.of("message", "요청 본문을 읽을 수 없습니다.");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleUnexpected(Exception exception) {
        return Map.of("message", "서버 오류가 발생했습니다.");
    }

    @ExceptionHandler(RegulationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    /** 선택한 법령이 없을 때 404를 반환한다. */
    public Map<String, String> handleNotFound(RegulationNotFoundException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(RiskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleRiskNotFound(RiskNotFoundException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler({InspectionAreaNotFoundException.class, SafetyInspectionNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleSafetyCheckNotFound(RuntimeException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(ReportNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleReportNotFound(ReportNotFoundException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(ReportDraftGenerationException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> handleReportDraftGeneration(ReportDraftGenerationException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(InvalidReportDraftException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> handleInvalidReportDraft(InvalidReportDraftException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(SimulationServiceTimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public Map<String, String> handleSimulationServiceTimeout(SimulationServiceTimeoutException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(SimulationServiceException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> handleSimulationService(SimulationServiceException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleForbidden(ForbiddenException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleInvalidToken(InvalidTokenException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(LawApiConfigurationException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    /** 로컬 환경에 인증값이 없을 때 외부 연동 불가 상태를 반환한다. */
    public Map<String, String> handleConfiguration(LawApiConfigurationException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(ResourceAccessException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public Map<String, String> handleLawApiTimeout(ResourceAccessException exception) {
        return Map.of("message", "Regulation service timed out.");
    }

    @ExceptionHandler(RestClientException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    /** 국가법령정보센터의 네트워크·HTTP 오류를 502로 감싼다. */
    public Map<String, String> handleLawApiFailure(RestClientException exception) {
        return Map.of("message", "Unable to retrieve regulation data.");
    }
}

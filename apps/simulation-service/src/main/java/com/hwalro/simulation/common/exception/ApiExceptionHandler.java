package com.hwalro.simulation.common.exception;

import com.hwalro.simulation.common.jwt.ForbiddenException;
import com.hwalro.simulation.common.jwt.InvalidTokenException;
import com.hwalro.simulation.drawing.exception.DrawingConflictException;
import com.hwalro.simulation.drawing.exception.DrawingDeletionNotAllowedException;
import com.hwalro.simulation.drawing.exception.DrawingLockedException;
import com.hwalro.simulation.drawing.exception.DrawingNotFoundException;
import com.hwalro.simulation.drawing.exception.DrawingValidationException;
import com.hwalro.simulation.result.exception.SimulationResultNotFoundException;
import com.hwalro.simulation.simulation.exception.InvalidSimulationGeometryException;
import com.hwalro.simulation.simulation.exception.SimulationConflictException;
import com.hwalro.simulation.simulation.exception.SimulationEngineUnavailableException;
import com.hwalro.simulation.simulation.exception.SimulationNotFoundException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(IllegalArgumentException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(DrawingValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleDrawingValidation(DrawingValidationException exception) {
        return Map.of("message", exception.getMessage(), "problems", exception.getProblems());
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
        log.error("처리되지 않은 예외가 발생했습니다.", exception);
        return Map.of("message", "서버 오류가 발생했습니다.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleDataIntegrity(DataIntegrityViolationException exception) {
        log.warn("데이터 무결성 제약 위반: {}", exception.getMessage());
        return Map.of("message", "저장하려는 데이터가 기존 데이터와 충돌합니다.");
    }

    @ExceptionHandler(DrawingNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleDrawingNotFound(DrawingNotFoundException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(SimulationResultNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleSimulationResultNotFound(SimulationResultNotFoundException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(DrawingDeletionNotAllowedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleDeletionNotAllowed(DrawingDeletionNotAllowedException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(DrawingConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleConflict(DrawingConflictException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(DrawingLockedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleDrawingLocked(DrawingLockedException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(SimulationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleSimulationNotFound(SimulationNotFoundException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(SimulationConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleSimulationConflict(SimulationConflictException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(SimulationEngineUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> handleSimulationEngineUnavailable(SimulationEngineUnavailableException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(InvalidSimulationGeometryException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, String> handleInvalidSimulationGeometry(InvalidSimulationGeometryException exception) {
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
}

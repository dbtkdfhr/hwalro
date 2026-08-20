package com.hwalro.simulation.drawing.exception;

import com.hwalro.simulation.drawing.dto.ValidationProblem;
import java.util.List;

public class DrawingValidationException extends IllegalArgumentException {
    private final List<ValidationProblem> problems;

    public DrawingValidationException(String message, List<ValidationProblem> problems) {
        super(message);
        this.problems = problems;
    }

    public List<ValidationProblem> getProblems() {
        return problems;
    }
}

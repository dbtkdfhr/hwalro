package com.hwalro.regulation.safetycheck.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.hwalro.regulation.common.jwt.RequireRole;
import com.hwalro.regulation.safetycheck.dto.InspectionAreaRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class SafetyCheckControllerTest {
    @Test
    void limitsAreaWritesToAdministratorsAndSafetyReviewers() throws NoSuchMethodException {
        assertWriteRoles(SafetyCheckController.class.getDeclaredMethod("createArea", InspectionAreaRequest.class));
        assertWriteRoles(
                SafetyCheckController.class.getDeclaredMethod("updateArea", Long.class, InspectionAreaRequest.class));
        assertWriteRoles(SafetyCheckController.class.getDeclaredMethod("deleteArea", Long.class));
    }

    private void assertWriteRoles(Method method) {
        assertThat(method.getAnnotation(RequireRole.class).value())
                .containsExactlyInAnyOrder("ADMIN", "SAFETY_REVIEWER");
    }
}

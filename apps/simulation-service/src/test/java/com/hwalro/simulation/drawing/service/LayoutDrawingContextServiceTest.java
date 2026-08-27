package com.hwalro.simulation.drawing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.common.jwt.ForbiddenException;
import com.hwalro.simulation.common.jwt.JwtUser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LayoutDrawingContextServiceTest {
    @Mock
    private DrawingService drawingService;

    @Test
    void omitsUnassignedLayoutsForStoreEmployee() {
        JwtUser employee = new JwtUser(9L, Set.of("GENERAL_EMPLOYEE"));
        when(drawingService.get(802L, employee)).thenThrow(new ForbiddenException("접근 권한이 없습니다."));

        List<?> contexts = new LayoutDrawingContextService(drawingService).findAll(List.of(802L), employee);

        assertThat(contexts).isEmpty();
    }
}

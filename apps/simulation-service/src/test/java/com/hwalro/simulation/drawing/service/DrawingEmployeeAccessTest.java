package com.hwalro.simulation.drawing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.common.jwt.ForbiddenException;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.drawing.DefaultDrawingData;
import com.hwalro.simulation.drawing.domain.Layout;
import com.hwalro.simulation.drawing.dto.DrawingListResponse;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.zone.mapper.LayoutZoneMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DrawingEmployeeAccessTest {
    private static final Long LAYOUT_ID = 802L;
    private static final Long VERSION_ID = 803L;
    private static final Long EMPLOYEE_ID = 9L;

    @Mock
    private DrawingMapper drawingMapper;

    @Mock
    private DefaultDrawingData defaultDrawingData;

    @Mock
    private LayoutGeometryValidator geometryValidator;

    @Mock
    private LayoutMetadataCopier layoutMetadataCopier;

    @Mock
    private LayoutZoneMapper layoutZoneMapper;

    private DrawingService service() {
        return new DrawingService(
                drawingMapper, defaultDrawingData, geometryValidator, layoutMetadataCopier, layoutZoneMapper);
    }

    private static JwtUser employee() {
        return new JwtUser(EMPLOYEE_ID, Set.of("GENERAL_EMPLOYEE"));
    }

    private static JwtUser operator() {
        return new JwtUser(7L, Set.of("OPERATOR"));
    }

    private static Layout layout(Long createdBy) {
        Layout layout = new Layout();
        layout.setId(LAYOUT_ID);
        layout.setCurrentVersionId(VERSION_ID);
        layout.setCreatedBy(createdBy);
        layout.setTitle("도면");
        return layout;
    }

    @Test
    void employeeListUsesTheAssignmentAxisAndHidesSimulationCounts() {
        when(drawingMapper.countLayoutsAssignedToUser(EMPLOYEE_ID, null)).thenReturn(1L);
        when(drawingMapper.findLayoutPageAssignedToUser(0, 20, EMPLOYEE_ID, null))
                .thenReturn(List.of(layout(7L)));

        DrawingListResponse response = service().list(1, 20, null, employee());

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).simulationCount()).isZero();
        verify(drawingMapper, never()).countLayouts(any(), any());
        verify(drawingMapper, never()).findLayoutPage(anyInt(), anyInt(), any(), any());
        verify(drawingMapper, never()).countSimulationsByLayoutIds(any());
    }

    @Test
    void privilegedListStillUsesTheCreatorAxis() {
        when(drawingMapper.countLayouts(7L, null)).thenReturn(1L);
        when(drawingMapper.findLayoutPage(0, 20, 7L, null)).thenReturn(List.of(layout(7L)));
        when(drawingMapper.countSimulationsByLayoutIds(List.of(LAYOUT_ID))).thenReturn(List.of());

        service().list(1, 20, null, operator());

        verify(drawingMapper).findLayoutPage(0, 20, 7L, null);
        verify(drawingMapper, never()).findLayoutPageAssignedToUser(anyInt(), anyInt(), anyLong(), any());
    }

    @Test
    void employeeCanOpenADrawingThatHoldsOneOfTheirZones() {
        when(drawingMapper.findLayoutById(LAYOUT_ID)).thenReturn(layout(7L));
        when(layoutZoneMapper.countZonesAssignedToUserInVersion(VERSION_ID, EMPLOYEE_ID))
                .thenReturn(1);

        service().requireAccessible(LAYOUT_ID, employee());
    }

    @Test
    void employeeCannotOpenADrawingWithoutAnAssignedZone() {
        when(drawingMapper.findLayoutById(LAYOUT_ID)).thenReturn(layout(7L));
        when(layoutZoneMapper.countZonesAssignedToUserInVersion(VERSION_ID, EMPLOYEE_ID))
                .thenReturn(0);
        DrawingService service = service();
        JwtUser employee = employee();

        assertThatThrownBy(() -> service.requireAccessible(LAYOUT_ID, employee)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aUserHoldingBothRolesIsTreatedAsPrivileged() {
        JwtUser dual = new JwtUser(EMPLOYEE_ID, Set.of("GENERAL_EMPLOYEE", "OPERATOR"));
        when(drawingMapper.countLayouts(EMPLOYEE_ID, null)).thenReturn(0L);
        when(drawingMapper.findLayoutPage(0, 20, EMPLOYEE_ID, null)).thenReturn(List.of());

        service().list(1, 20, null, dual);

        verify(drawingMapper).findLayoutPage(0, 20, EMPLOYEE_ID, null);
        verify(drawingMapper, never()).findLayoutPageAssignedToUser(anyInt(), anyInt(), anyLong(), any());
    }
}

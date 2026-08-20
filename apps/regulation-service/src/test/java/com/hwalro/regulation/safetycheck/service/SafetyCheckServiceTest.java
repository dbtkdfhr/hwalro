package com.hwalro.regulation.safetycheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hwalro.regulation.common.jwt.JwtUser;
import com.hwalro.regulation.safetycheck.domain.ChecklistTemplate;
import com.hwalro.regulation.safetycheck.domain.InspectionArea;
import com.hwalro.regulation.safetycheck.domain.SafetyInspection;
import com.hwalro.regulation.safetycheck.dto.ChecklistTemplateUpdateRequest;
import com.hwalro.regulation.safetycheck.dto.InspectionAreaRequest;
import com.hwalro.regulation.safetycheck.dto.InspectionAreaResponse;
import com.hwalro.regulation.safetycheck.dto.InspectionCreateRequest;
import com.hwalro.regulation.safetycheck.dto.InspectionDetailHeader;
import com.hwalro.regulation.safetycheck.dto.InspectionDetailResponse;
import com.hwalro.regulation.safetycheck.dto.InspectionUpdateRequest;
import com.hwalro.regulation.safetycheck.exception.InspectionAreaNotFoundException;
import com.hwalro.regulation.safetycheck.mapper.SafetyCheckMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SafetyCheckServiceTest {
    @Mock
    private SafetyCheckMapper safetyCheckMapper;

    @Test
    void createsAreaWithNormalizedFields() {
        SafetyCheckService service = new SafetyCheckService(safetyCheckMapper);
        InspectionAreaResponse created = new InspectionAreaResponse(41L, "Lobby", null, true, 0, null, true);
        doAnswer(invocation -> {
                    invocation.<InspectionArea>getArgument(0).setId(41L);
                    return 1;
                })
                .when(safetyCheckMapper)
                .insertArea(any());
        when(safetyCheckMapper.findArea(41L, null)).thenReturn(created);

        InspectionAreaResponse response = service.createArea(new InspectionAreaRequest("  Lobby  ", "   "));

        assertThat(response).isSameAs(created);
        verify(safetyCheckMapper)
                .insertArea(argThat(area -> "Lobby".equals(area.getName()) && area.getDescription() == null));
    }

    @Test
    void getsInactiveAreaById() {
        SafetyCheckService service = new SafetyCheckService(safetyCheckMapper);
        JwtUser operator = new JwtUser(3L, Set.of("OPERATOR"));
        InspectionAreaResponse inactive = new InspectionAreaResponse(41L, "Lobby", null, false, 2, null, true);
        when(safetyCheckMapper.findArea(41L, 3L)).thenReturn(inactive);

        assertThat(service.getArea(41L, operator)).isSameAs(inactive);
    }

    @Test
    void readsHistoryAndTemplateForInactiveArea() {
        SafetyCheckService service = new SafetyCheckService(safetyCheckMapper);
        JwtUser reviewer = new JwtUser(3L, Set.of("SAFETY_REVIEWER"));
        InspectionAreaResponse inactive = new InspectionAreaResponse(41L, "Lobby", null, false, 2, null, true);
        when(safetyCheckMapper.findArea(41L, null)).thenReturn(inactive);
        when(safetyCheckMapper.findInspectionHistory(41L, null)).thenReturn(List.of());
        when(safetyCheckMapper.findActiveTemplateId(41L)).thenReturn(null);

        assertThat(service.getInspectionHistory(41L, reviewer)).isEmpty();
        assertThat(service.getChecklistTemplate(41L).id()).isNull();
    }

    @Test
    void updatesOnlyActiveArea() {
        SafetyCheckService service = new SafetyCheckService(safetyCheckMapper);
        InspectionAreaResponse updated = new InspectionAreaResponse(41L, "Hall", "North", true, 0, null, true);
        when(safetyCheckMapper.updateArea(any())).thenReturn(1);
        when(safetyCheckMapper.findArea(41L, null)).thenReturn(updated);

        assertThat(service.updateArea(41L, new InspectionAreaRequest(" Hall ", " North ")))
                .isSameAs(updated);
        verify(safetyCheckMapper)
                .updateArea(argThat(area -> area.getId().equals(41L)
                        && "Hall".equals(area.getName())
                        && "North".equals(area.getDescription())));
    }

    @Test
    void softDeletesActiveArea() {
        SafetyCheckService service = new SafetyCheckService(safetyCheckMapper);
        when(safetyCheckMapper.deactivateArea(41L)).thenReturn(1);

        service.deleteArea(41L);

        verify(safetyCheckMapper).deactivateArea(41L);
    }

    @Test
    void validatesAreaFields() {
        SafetyCheckService service = new SafetyCheckService(safetyCheckMapper);

        assertThatThrownBy(() -> service.createArea(new InspectionAreaRequest("   ", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Inspection area name is required.");
        assertThatThrownBy(() -> service.createArea(new InspectionAreaRequest("x".repeat(201), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Inspection area name must be 200 characters or fewer.");
        assertThatThrownBy(() -> service.createArea(new InspectionAreaRequest("Lobby", "x".repeat(1001))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Inspection area description must be 1000 characters or fewer.");
        verify(safetyCheckMapper, never()).insertArea(any());
    }

    @Test
    void createsInspectionFromAreasActiveTemplate() {
        SafetyCheckService service = new SafetyCheckService(safetyCheckMapper);
        JwtUser inspector = new JwtUser(3L, Set.of("SAFETY_REVIEWER"));
        when(safetyCheckMapper.areaExists(2L)).thenReturn(true);
        when(safetyCheckMapper.findActiveTemplateId(2L)).thenReturn(7L);
        doAnswer(invocation -> {
                    SafetyInspection inspection = invocation.getArgument(0);
                    inspection.setId(12L);
                    return 1;
                })
                .when(safetyCheckMapper)
                .insertInspection(any(SafetyInspection.class));
        when(safetyCheckMapper.findInspectionHeader(12L))
                .thenReturn(
                        new InspectionDetailHeader(12L, 2L, "B2", null, 3L, "DRAFT", null, LocalDateTime.now(), null));
        when(safetyCheckMapper.findInspectionItems(12L)).thenReturn(List.of());

        service.createInspection(2L, null, inspector);

        verify(safetyCheckMapper).insertInspectionItems(12L, 7L);
    }

    @Test
    void returnsExistingOpenDraftInsteadOfCreatingAnother() {
        SafetyCheckService service = new SafetyCheckService(safetyCheckMapper);
        JwtUser inspector = new JwtUser(3L, Set.of("SAFETY_REVIEWER"));
        when(safetyCheckMapper.lockInspectionArea(2L)).thenReturn(2L);
        when(safetyCheckMapper.findOpenDraftId(2L, 3L)).thenReturn(12L);
        when(safetyCheckMapper.findInspectionHeader(12L))
                .thenReturn(
                        new InspectionDetailHeader(12L, 2L, "B2", null, 3L, "DRAFT", null, LocalDateTime.now(), null));
        when(safetyCheckMapper.findInspectionItems(12L)).thenReturn(List.of());

        InspectionDetailResponse response = service.getOrCreateOpenInspection(2L, inspector);

        assertThat(response.id()).isEqualTo(12L);
        verify(safetyCheckMapper, never()).insertInspection(any(SafetyInspection.class));
    }

    @Test
    void createsOpenDraftWhenNoneExists() {
        SafetyCheckService service = new SafetyCheckService(safetyCheckMapper);
        JwtUser inspector = new JwtUser(3L, Set.of("SAFETY_REVIEWER"));
        when(safetyCheckMapper.lockInspectionArea(2L)).thenReturn(2L);
        when(safetyCheckMapper.findOpenDraftId(2L, 3L)).thenReturn(null);
        when(safetyCheckMapper.areaExists(2L)).thenReturn(true);
        when(safetyCheckMapper.findActiveTemplateId(2L)).thenReturn(7L);
        doAnswer(invocation -> {
                    SafetyInspection inspection = invocation.getArgument(0);
                    inspection.setId(12L);
                    return 1;
                })
                .when(safetyCheckMapper)
                .insertInspection(any(SafetyInspection.class));
        when(safetyCheckMapper.findInspectionHeader(12L))
                .thenReturn(
                        new InspectionDetailHeader(12L, 2L, "B2", null, 3L, "DRAFT", null, LocalDateTime.now(), null));
        when(safetyCheckMapper.findInspectionItems(12L)).thenReturn(List.of());

        InspectionDetailResponse response = service.getOrCreateOpenInspection(2L, inspector);

        assertThat(response.id()).isEqualTo(12L);
        verify(safetyCheckMapper).insertInspectionItems(12L, 7L);
    }

    @Test
    void rejectsCompletionWhileAnItemIsPending() {
        SafetyCheckService service = new SafetyCheckService(safetyCheckMapper);
        JwtUser inspector = new JwtUser(3L, Set.of("SAFETY_REVIEWER"));
        when(safetyCheckMapper.findInspectionHeader(12L))
                .thenReturn(
                        new InspectionDetailHeader(12L, 2L, "B2", null, 3L, "DRAFT", null, LocalDateTime.now(), null));
        when(safetyCheckMapper.areaExists(2L)).thenReturn(true);
        when(safetyCheckMapper.countInspectionItems(12L)).thenReturn(1);
        when(safetyCheckMapper.updateInspectionItem(any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(safetyCheckMapper.countPendingItems(12L)).thenReturn(1);
        InspectionUpdateRequest request = new InspectionUpdateRequest(
                "COMPLETED", null, List.of(new InspectionUpdateRequest.ItemUpdate(22L, "PENDING", null)));

        assertThatThrownBy(() -> service.updateInspection(12L, request, inspector))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("All checklist items must be assessed before completion.");
    }

    @Test
    void rejectsAnyUpdateAfterInspectionIsCompleted() {
        SafetyCheckService service = new SafetyCheckService(safetyCheckMapper);
        JwtUser inspector = new JwtUser(3L, Set.of("SAFETY_REVIEWER"));
        when(safetyCheckMapper.findInspectionHeader(12L))
                .thenReturn(new InspectionDetailHeader(
                        12L, 2L, "B2", null, 3L, "COMPLETED", null, LocalDateTime.now(), LocalDateTime.now()));
        when(safetyCheckMapper.areaExists(2L)).thenReturn(true);
        InspectionUpdateRequest request = new InspectionUpdateRequest(
                "DRAFT", null, List.of(new InspectionUpdateRequest.ItemUpdate(22L, "PASS", null)));

        assertThatThrownBy(() -> service.updateInspection(12L, request, inspector))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Completed inspections cannot be modified.");
    }

    @Test
    void updatesChecklistAsANewTemplateVersion() {
        SafetyCheckService service = new SafetyCheckService(safetyCheckMapper);
        when(safetyCheckMapper.findArea(2L, null))
                .thenReturn(new InspectionAreaResponse(2L, "B2", null, true, 0, null, true));
        when(safetyCheckMapper.lockInspectionArea(2L)).thenReturn(2L);
        when(safetyCheckMapper.findNextTemplateVersion(2L)).thenReturn(3);
        doAnswer(invocation -> {
                    ChecklistTemplate template = invocation.getArgument(0);
                    template.setId(9L);
                    return 1;
                })
                .when(safetyCheckMapper)
                .insertChecklistTemplate(any(ChecklistTemplate.class));
        when(safetyCheckMapper.findActiveTemplateId(2L)).thenReturn(9L);
        when(safetyCheckMapper.findTemplateVersion(9L)).thenReturn(3);
        when(safetyCheckMapper.findTemplateItems(9L)).thenReturn(List.of());
        ChecklistTemplateUpdateRequest request = new ChecklistTemplateUpdateRequest(
                List.of(new ChecklistTemplateUpdateRequest.ItemInput("비상구 확인", "장애물이 없어야 함", "EVACUATION")));

        service.updateChecklistTemplate(2L, request);

        InOrder allocationOrder = inOrder(safetyCheckMapper);
        allocationOrder.verify(safetyCheckMapper).lockInspectionArea(2L);
        allocationOrder.verify(safetyCheckMapper).findNextTemplateVersion(2L);
        verify(safetyCheckMapper).retireActiveTemplates(2L);
        verify(safetyCheckMapper).insertChecklistTemplateItem(9L, "비상구 확인", "장애물이 없어야 함", "EVACUATION", 1);
    }

    @Test
    void deletesDraftInspectionOwnedByRequester() {
        SafetyCheckService service = new SafetyCheckService(safetyCheckMapper);
        JwtUser inspector = new JwtUser(3L, Set.of("SAFETY_REVIEWER"));
        when(safetyCheckMapper.findInspectionHeader(12L))
                .thenReturn(
                        new InspectionDetailHeader(12L, 2L, "B2", null, 3L, "DRAFT", null, LocalDateTime.now(), null));
        when(safetyCheckMapper.deleteInspection(12L)).thenReturn(1);

        service.deleteInspection(12L, inspector);

        verify(safetyCheckMapper).deleteInspection(12L);
    }

    @Test
    void rejectsDeletingCompletedInspection() {
        SafetyCheckService service = new SafetyCheckService(safetyCheckMapper);
        JwtUser inspector = new JwtUser(3L, Set.of("SAFETY_REVIEWER"));
        when(safetyCheckMapper.findInspectionHeader(12L))
                .thenReturn(new InspectionDetailHeader(
                        12L, 2L, "B2", null, 3L, "COMPLETED", null, LocalDateTime.now(), LocalDateTime.now()));

        assertThatThrownBy(() -> service.deleteInspection(12L, inspector))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Completed inspections cannot be deleted.");
    }

    @Test
    void rejectsUnvalidatedSimulationResultReference() {
        SafetyCheckService service = new SafetyCheckService(safetyCheckMapper);
        JwtUser inspector = new JwtUser(3L, Set.of("SAFETY_REVIEWER"));

        assertThatThrownBy(() -> service.createInspection(2L, new InspectionCreateRequest(55L), inspector))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("simulationResultId cannot be linked until simulation-service provides a validation API.");
        verify(safetyCheckMapper, never()).insertInspection(any(SafetyInspection.class));
    }

    @Test
    void rollsBackWhenAnotherRequestCompletesInspectionFirst() {
        SafetyCheckService service = new SafetyCheckService(safetyCheckMapper);
        JwtUser inspector = new JwtUser(3L, Set.of("SAFETY_REVIEWER"));
        when(safetyCheckMapper.findInspectionHeader(12L))
                .thenReturn(
                        new InspectionDetailHeader(12L, 2L, "B2", null, 3L, "DRAFT", null, LocalDateTime.now(), null));
        when(safetyCheckMapper.areaExists(2L)).thenReturn(true);
        when(safetyCheckMapper.countInspectionItems(12L)).thenReturn(1);
        when(safetyCheckMapper.updateInspectionItem(any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(safetyCheckMapper.updateInspection(any(), any(), any(), any())).thenReturn(0);
        InspectionUpdateRequest request = new InspectionUpdateRequest(
                "DRAFT", null, List.of(new InspectionUpdateRequest.ItemUpdate(22L, "PASS", null)));

        assertThatThrownBy(() -> service.updateInspection(12L, request, inspector))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The inspection was already completed by another request.");
    }

    @Test
    void rejectsUpdatingInspectionForInactiveArea() {
        SafetyCheckService service = new SafetyCheckService(safetyCheckMapper);
        JwtUser inspector = new JwtUser(3L, Set.of("SAFETY_REVIEWER"));
        when(safetyCheckMapper.findInspectionHeader(12L))
                .thenReturn(
                        new InspectionDetailHeader(12L, 2L, "B2", null, 3L, "DRAFT", null, LocalDateTime.now(), null));
        InspectionUpdateRequest request = new InspectionUpdateRequest("DRAFT", null, List.of());

        assertThatThrownBy(() -> service.updateInspection(12L, request, inspector))
                .isInstanceOf(InspectionAreaNotFoundException.class);
        verify(safetyCheckMapper, never()).updateInspection(any(), any(), any(), any());
    }
}

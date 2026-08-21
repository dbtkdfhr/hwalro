package com.hwalro.simulation.simulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.common.jwt.ForbiddenException;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.drawing.domain.OutsideWall;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.simulation.client.RegulationUsageClient;
import com.hwalro.simulation.simulation.domain.HazardZone;
import com.hwalro.simulation.simulation.domain.LayoutSimulationContext;
import com.hwalro.simulation.simulation.domain.Simulation;
import com.hwalro.simulation.simulation.domain.SimulationOption;
import com.hwalro.simulation.simulation.dto.SimulationDtos.DraftCreateRequest;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PlacementAdjustmentDraftRequest;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SetupUpdateRequest;
import com.hwalro.simulation.simulation.exception.InvalidSimulationGeometryException;
import com.hwalro.simulation.simulation.exception.SimulationConflictException;
import com.hwalro.simulation.simulation.exception.SimulationNotFoundException;
import com.hwalro.simulation.simulation.mapper.SimulationMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class SimulationServiceTest {
    @Mock
    private SimulationMapper simulationMapper;

    @Mock
    private DrawingMapper drawingMapper;

    @Mock
    private RegulationUsageClient regulationUsageClient;

    private SimulationService service;
    private JwtUser user;

    @BeforeEach
    void setUp() {
        service = new SimulationService(
                simulationMapper,
                drawingMapper,
                new ObjectMapper(),
                regulationUsageClient,
                new TransactionTemplate(new NoOpTransactionManager()));
        user = new JwtUser(7L, Set.of("OPERATOR"));
    }

    @Test
    void copiesSameVersionPositionsAndLocksDrawingVersion() {
        LayoutSimulationContext context = context("초안");
        when(simulationMapper.findLayoutContextForUpdate(11L)).thenReturn(context);
        when(simulationMapper.findLayoutContext(11L)).thenReturn(context);
        stubDrawing();
        when(simulationMapper.insertSimulation(any())).thenAnswer(invocation -> {
            Simulation simulation = invocation.getArgument(0);
            simulation.setId(21L);
            return 1;
        });
        when(simulationMapper.findSimulationById(20L)).thenReturn(parentSimulation(11L));
        when(simulationMapper.lockLayoutVersion(11L)).thenReturn(1);
        Simulation created = simulation();
        created.setParentSimulationId(20L);
        when(simulationMapper.findSimulationById(21L)).thenReturn(created);
        when(simulationMapper.findSimulationOption(21L)).thenReturn(option());
        when(simulationMapper.findInitialStateJson(20L)).thenReturn("[[1,1],[2,2]]");
        when(simulationMapper.findInitialStateJson(21L)).thenReturn("[[1,1],[2,2]]");
        when(simulationMapper.findHazardZones(21L)).thenReturn(List.of());
        when(simulationMapper.findSelectedExitIds(21L)).thenReturn(List.of());

        var response = service.createDraft(new DraftCreateRequest(11L, 20L), user);

        assertThat(response.simulationId()).isEqualTo(21L);
        assertThat(response.totalPeople()).isEqualTo(2);
        assertThat(response.parentSimulationId()).isEqualTo(20L);
        verify(simulationMapper).lockLayoutVersion(11L);
        ArgumentCaptor<SimulationOption> optionCaptor = ArgumentCaptor.forClass(SimulationOption.class);
        verify(simulationMapper).insertSimulationOption(optionCaptor.capture());
        assertThat(optionCaptor.getValue().getModelProfile()).isEqualTo("SFM_DEFAULT_V2");
        assertThat(optionCaptor.getValue().getRoutingProfile()).isEqualTo("HAZARD_RADIAL_EXP_V3");
        assertThat(optionCaptor.getValue().getTotalPeople()).isEqualTo(2);
    }

    @Test
    void rejectsDraftWhenOutsideBoundaryIsMissing() {
        when(simulationMapper.findLayoutContextForUpdate(11L)).thenReturn(context("초안"));
        when(drawingMapper.findWallsByVersionId(11L)).thenReturn(List.of());
        when(drawingMapper.findOutsideWallsByVersionId(11L)).thenReturn(List.of());
        when(drawingMapper.findPillarsByVersionId(11L)).thenReturn(List.of());
        when(drawingMapper.findFabricsByVersionId(11L)).thenReturn(List.of());
        when(drawingMapper.findLayoutTextsByVersionId(11L)).thenReturn(List.of());
        when(drawingMapper.findLayoutExitsByVersionId(11L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createDraft(new DraftCreateRequest(11L, null), user))
                .isInstanceOf(com.hwalro.simulation.simulation.exception.InvalidSimulationGeometryException.class);
    }

    @Test
    void rejectsCopyFromAnotherLayoutVersion() {
        when(simulationMapper.findLayoutContextForUpdate(11L)).thenReturn(context("잠금"));
        stubDrawing();
        when(simulationMapper.findSimulationById(20L)).thenReturn(parentSimulation(12L));

        assertThatThrownBy(() -> service.createDraft(new DraftCreateRequest(11L, 20L), user))
                .isInstanceOf(SimulationConflictException.class);
    }

    @Test
    void createsAdjustmentDraftWithRecommendationAndCopiesWholeSetup() {
        Simulation source = parentSimulation(11L);
        source.setStatus("FAILED");
        source.setFailureDetail(routeFailureJson("2", "2", "2", "3", "3"));
        when(simulationMapper.findSimulationByIdForUpdate(20L)).thenReturn(source);
        when(simulationMapper.findInitialStateJson(20L)).thenReturn("[[1,1],[2,2]]");
        SimulationOption sourceOption = option();
        sourceOption.setSimulationId(20L);
        sourceOption.setRandomSeed(99);
        sourceOption.setWalkingSpeed(BigDecimal.valueOf(1.4));
        sourceOption.setReactionTime(BigDecimal.valueOf(0.7));
        sourceOption.setInitialResponseTimeStdDev(BigDecimal.valueOf(3));
        when(simulationMapper.findSimulationOption(20L)).thenReturn(sourceOption);
        HazardZone sourceHazard = hazard(20L, 6, 6, 1);
        when(simulationMapper.findHazardZones(20L)).thenReturn(List.of(sourceHazard));
        when(simulationMapper.findSelectedExitIds(20L)).thenReturn(List.of(501L));
        when(simulationMapper.findLayoutContext(11L)).thenReturn(context("잠금"));
        stubDrawing();
        when(simulationMapper.insertSimulation(any())).thenAnswer(invocation -> {
            Simulation draft = invocation.getArgument(0);
            draft.setId(21L);
            return 1;
        });
        Simulation created = simulation();
        created.setParentSimulationId(20L);
        when(simulationMapper.findSimulationById(21L)).thenReturn(created);
        when(simulationMapper.findSimulationOption(21L)).thenReturn(sourceOption);
        when(simulationMapper.findInitialStateJson(21L)).thenReturn("[[1,1],[3,3]]");
        when(simulationMapper.findHazardZones(21L)).thenReturn(List.of(sourceHazard));
        when(simulationMapper.findSelectedExitIds(21L)).thenReturn(List.of(501L));

        var response = service.createPlacementAdjustmentDraft(20L, new PlacementAdjustmentDraftRequest(true), user);

        assertThat(response.parentSimulationId()).isEqualTo(20L);
        assertThat(response.agentPositions().get(1).x()).isEqualByComparingTo(BigDecimal.valueOf(3));
        ArgumentCaptor<Simulation> draftCaptor = ArgumentCaptor.forClass(Simulation.class);
        verify(simulationMapper).insertSimulation(draftCaptor.capture());
        assertThat(draftCaptor.getValue().getParentSimulationId()).isEqualTo(20L);
        ArgumentCaptor<SimulationOption> optionCaptor = ArgumentCaptor.forClass(SimulationOption.class);
        verify(simulationMapper).insertSimulationOption(optionCaptor.capture());
        assertThat(optionCaptor.getValue().getRandomSeed()).isEqualTo(99);
        assertThat(optionCaptor.getValue().getModelProfile()).isEqualTo(sourceOption.getModelProfile());
        assertThat(optionCaptor.getValue().getRoutingProfile()).isEqualTo(sourceOption.getRoutingProfile());
        assertThat(optionCaptor.getValue().getWalkingSpeed()).isEqualByComparingTo("1.4");
        assertThat(optionCaptor.getValue().getReactionTime()).isEqualByComparingTo("0.5");
        assertThat(optionCaptor.getValue().getInitialResponseTimeStdDev()).isEqualByComparingTo("3");
        verify(simulationMapper).insertInitialState(21L, "[[1,1],[3,3]]");
        ArgumentCaptor<List<HazardZone>> hazardsCaptor = ArgumentCaptor.forClass(List.class);
        verify(simulationMapper).insertHazardZones(hazardsCaptor.capture());
        assertThat(hazardsCaptor.getValue()).singleElement().satisfies(hazard -> {
            assertThat(hazard.getSimulationId()).isEqualTo(21L);
            assertThat(hazard.getCenterX()).isEqualByComparingTo(BigDecimal.valueOf(6));
        });
        verify(simulationMapper).insertSimulationExits(21L, 11L, List.of(501L));
        verify(simulationMapper, never()).updateInitialState(eq(20L), any());
    }

    @Test
    void rejectsApplyingMissingRecommendationAsInvalidGeometry() {
        Simulation source = parentSimulation(11L);
        source.setStatus("FAILED");
        source.setFailureDetail(routeFailureJson("2", "2", "2", null, null));
        when(simulationMapper.findSimulationByIdForUpdate(20L)).thenReturn(source);
        when(simulationMapper.findInitialStateJson(20L)).thenReturn("[[1,1],[2,2]]");
        when(simulationMapper.findSimulationOption(20L)).thenReturn(option());
        when(simulationMapper.findHazardZones(20L)).thenReturn(List.of());
        when(simulationMapper.findSelectedExitIds(20L)).thenReturn(List.of(501L));

        assertThatThrownBy(() ->
                        service.createPlacementAdjustmentDraft(20L, new PlacementAdjustmentDraftRequest(true), user))
                .isInstanceOf(InvalidSimulationGeometryException.class);

        verify(simulationMapper, never()).insertSimulation(any());
    }

    @Test
    void copiesAgentOrderUnchangedWhenRecommendationIsNotApplied() {
        Simulation source = parentSimulation(11L);
        source.setStatus("FAILED");
        source.setFailureDetail(routeFailureJson("2", "2", "2", "9", "9"));
        when(simulationMapper.findSimulationByIdForUpdate(20L)).thenReturn(source);
        when(simulationMapper.findInitialStateJson(20L)).thenReturn("[[1,1],[2,2]]");
        when(simulationMapper.findSimulationOption(20L)).thenReturn(option());
        when(simulationMapper.findHazardZones(20L)).thenReturn(List.of());
        when(simulationMapper.findSelectedExitIds(20L)).thenReturn(List.of());
        when(simulationMapper.findLayoutContext(11L)).thenReturn(context("잠금"));
        stubDrawing();
        when(simulationMapper.insertSimulation(any())).thenAnswer(invocation -> {
            Simulation draft = invocation.getArgument(0);
            draft.setId(21L);
            return 1;
        });
        Simulation created = simulation();
        created.setParentSimulationId(20L);
        when(simulationMapper.findSimulationById(21L)).thenReturn(created);
        when(simulationMapper.findSimulationOption(21L)).thenReturn(option());
        when(simulationMapper.findInitialStateJson(21L)).thenReturn("[[1,1],[2,2]]");
        when(simulationMapper.findHazardZones(21L)).thenReturn(List.of());
        when(simulationMapper.findSelectedExitIds(21L)).thenReturn(List.of());

        service.createPlacementAdjustmentDraft(20L, new PlacementAdjustmentDraftRequest(false), user);

        verify(simulationMapper).insertInitialState(21L, "[[1,1],[2,2]]");
    }

    @Test
    void validatesUnchangedPlacementBeforeCopyingDraft() {
        Simulation source = parentSimulation(11L);
        source.setStatus("FAILED");
        source.setFailureDetail(routeFailureJson("2", "1.2", "1.2", "3", "3"));
        when(simulationMapper.findSimulationByIdForUpdate(20L)).thenReturn(source);
        when(simulationMapper.findInitialStateJson(20L)).thenReturn("[[1,1],[1.2,1.2]]");
        when(simulationMapper.findSimulationOption(20L)).thenReturn(option());
        when(simulationMapper.findHazardZones(20L)).thenReturn(List.of());
        when(simulationMapper.findSelectedExitIds(20L)).thenReturn(List.of());
        when(simulationMapper.findLayoutContext(11L)).thenReturn(context("잠금"));
        stubDrawing();

        assertThatThrownBy(() ->
                        service.createPlacementAdjustmentDraft(20L, new PlacementAdjustmentDraftRequest(false), user))
                .isInstanceOf(InvalidSimulationGeometryException.class);

        verify(simulationMapper, never()).insertSimulation(any());
    }

    @Test
    void validatesRecommendedPositionAgainstWholeSetupBeforeCreatingDraft() {
        Simulation source = parentSimulation(11L);
        source.setStatus("FAILED");
        source.setFailureDetail(routeFailureJson("2", "2", "2", "1.2", "1.2"));
        when(simulationMapper.findSimulationByIdForUpdate(20L)).thenReturn(source);
        when(simulationMapper.findInitialStateJson(20L)).thenReturn("[[1,1],[2,2]]");
        when(simulationMapper.findSimulationOption(20L)).thenReturn(option());
        when(simulationMapper.findHazardZones(20L)).thenReturn(List.of());
        when(simulationMapper.findSelectedExitIds(20L)).thenReturn(List.of());
        when(simulationMapper.findLayoutContext(11L)).thenReturn(context("잠금"));
        stubDrawing();

        assertThatThrownBy(() ->
                        service.createPlacementAdjustmentDraft(20L, new PlacementAdjustmentDraftRequest(true), user))
                .isInstanceOf(InvalidSimulationGeometryException.class);

        verify(simulationMapper, never()).insertSimulation(any());
    }

    @Test
    void rejectsFailureDetailThatDoesNotMatchStoredAgentPosition() {
        Simulation source = parentSimulation(11L);
        source.setStatus("FAILED");
        source.setFailureDetail(routeFailureJson("2", "9", "9", "3", "3"));
        when(simulationMapper.findSimulationByIdForUpdate(20L)).thenReturn(source);
        when(simulationMapper.findInitialStateJson(20L)).thenReturn("[[1,1],[2,2]]");

        assertThatThrownBy(() ->
                        service.createPlacementAdjustmentDraft(20L, new PlacementAdjustmentDraftRequest(false), user))
                .isInstanceOf(SimulationConflictException.class);

        verify(simulationMapper, never()).insertSimulation(any());
    }

    @Test
    void rejectsAdjustmentDraftWithoutSourceAccess() {
        Simulation source = parentSimulation(11L);
        source.setCreatedBy(8L);
        source.setStatus("FAILED");
        when(simulationMapper.findSimulationByIdForUpdate(20L)).thenReturn(source);

        assertThatThrownBy(() ->
                        service.createPlacementAdjustmentDraft(20L, new PlacementAdjustmentDraftRequest(false), user))
                .isInstanceOf(ForbiddenException.class);

        verify(simulationMapper, never()).findInitialStateJson(20L);
    }

    @Test
    void rejectsAdjustmentDraftFromNonFailedSource() {
        Simulation source = parentSimulation(11L);
        source.setStatus("COMPLETED");
        when(simulationMapper.findSimulationByIdForUpdate(20L)).thenReturn(source);

        assertThatThrownBy(() ->
                        service.createPlacementAdjustmentDraft(20L, new PlacementAdjustmentDraftRequest(false), user))
                .isInstanceOf(SimulationConflictException.class);

        verify(simulationMapper, never()).findInitialStateJson(20L);
    }

    @Test
    void rejectsUpdateOutsideDraftStatus() {
        Simulation completed = simulation();
        completed.setStatus("COMPLETED");
        when(simulationMapper.findSimulationByIdForUpdate(21L)).thenReturn(completed);

        assertThatThrownBy(() -> service.updateSetup(
                        21L,
                        new SetupUpdateRequest(
                                List.of(), List.of(), List.of(), BigDecimal.valueOf(1.25), BigDecimal.ZERO),
                        user))
                .isInstanceOf(SimulationConflictException.class);
        verifyNoInteractions(drawingMapper);
    }

    @Test
    void rejectsInitialResponseStdDevAboveMaximum() {
        SetupUpdateRequest request = new SetupUpdateRequest(
                List.of(), List.of(), List.of(), BigDecimal.valueOf(1.25), BigDecimal.valueOf(601));

        assertThatThrownBy(() -> service.updateSetup(21L, request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("출발시간 표준편차");
        verifyNoInteractions(simulationMapper, drawingMapper);
    }

    @Test
    void listsOnlyOperatorsOwnSimulationsWithPaging() {
        Simulation item = simulation();
        item.setLayoutId(3L);
        item.setLayoutTitle("test");
        item.setLayoutVersionNumber(1);
        item.setTotalPeople(12);
        when(simulationMapper.countSimulationOverview(7L, null)).thenReturn(1L);
        when(simulationMapper.findSimulationOverviewPage(0, 20, 7L, null)).thenReturn(List.of(item));

        var response = service.listOverview(1, 20, user);

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.items().get(0).layoutTitle()).isEqualTo("test");
        assertThat(response.items().get(0).totalPeople()).isEqualTo(12);
    }

    @Test
    void listsOnlyCurrentUsersMonitorItemsEvenForAdmin() {
        JwtUser admin = new JwtUser(7L, Set.of("ADMIN"));
        Simulation item = simulation();
        item.setLayoutTitle("test");
        when(simulationMapper.findSimulationMonitor(7L)).thenReturn(List.of(item));

        var response = service.listMonitor(admin);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(21L);
        verify(simulationMapper).findSimulationMonitor(7L);
    }

    @Test
    void returnsSingleOverviewForOwnSimulation() {
        Simulation item = simulation();
        item.setLayoutId(3L);
        item.setLayoutTitle("test");
        item.setLayoutVersionNumber(1);
        item.setTotalPeople(12);
        when(simulationMapper.findSimulationOverviewById(21L)).thenReturn(item);

        var response = service.getOverview(21L, user);

        assertThat(response.id()).isEqualTo(21L);
        assertThat(response.layoutTitle()).isEqualTo("test");
        assertThat(response.layoutId()).isEqualTo(3L);
    }

    @Test
    void rejectsSingleOverviewForOtherOperatorsSimulation() {
        Simulation item = simulation();
        item.setCreatedBy(99L);
        when(simulationMapper.findSimulationOverviewById(21L)).thenReturn(item);

        assertThatThrownBy(() -> service.getOverview(21L, user)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rejectsSingleOverviewWhenSimulationMissing() {
        when(simulationMapper.findSimulationOverviewById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.getOverview(404L, user)).isInstanceOf(SimulationNotFoundException.class);
    }

    @Test
    void returnsWorkSummaryScopedToCurrentUser() {
        when(simulationMapper.countInProgress(7L)).thenReturn(1L);
        when(simulationMapper.countCompletedThisWeek(eq(7L), any(LocalDateTime.class)))
                .thenReturn(7L);

        var response = service.getWorkSummary(user);

        assertThat(response.inProgressCount()).isEqualTo(1);
        assertThat(response.completedThisWeekCount()).isEqualTo(7);
        verify(simulationMapper).countInProgress(7L);
        verify(simulationMapper).countCompletedThisWeek(eq(7L), any(LocalDateTime.class));
    }

    @Test
    void scopesWorkSummaryToCurrentUserEvenForAdmin() {
        JwtUser admin = new JwtUser(7L, Set.of("ADMIN"));
        when(simulationMapper.countInProgress(7L)).thenReturn(0L);
        when(simulationMapper.countCompletedThisWeek(eq(7L), any(LocalDateTime.class)))
                .thenReturn(0L);

        service.getWorkSummary(admin);

        verify(simulationMapper).countInProgress(7L);
        verify(simulationMapper).countCompletedThisWeek(eq(7L), any(LocalDateTime.class));
    }

    private void stubDrawing() {
        when(drawingMapper.findWallsByVersionId(11L)).thenReturn(List.of());
        when(drawingMapper.findOutsideWallsByVersionId(11L))
                .thenReturn(List.of(wall(0, 0, 10, 0), wall(10, 0, 10, 10), wall(10, 10, 0, 10), wall(0, 10, 0, 0)));
        when(drawingMapper.findPillarsByVersionId(11L)).thenReturn(List.of());
        when(drawingMapper.findFabricsByVersionId(11L)).thenReturn(List.of());
        when(drawingMapper.findLayoutTextsByVersionId(11L)).thenReturn(List.of());
        when(drawingMapper.findLayoutExitsByVersionId(11L)).thenReturn(List.of());
    }

    private static LayoutSimulationContext context(String status) {
        LayoutSimulationContext context = new LayoutSimulationContext();
        context.setLayoutVersionId(11L);
        context.setLayoutId(3L);
        context.setCreatedBy(7L);
        context.setTitle("test");
        context.setLayoutVersionNumber(1);
        context.setLayoutVersionStatus(status);
        context.setWidth(BigDecimal.TEN);
        context.setHeight(BigDecimal.TEN);
        return context;
    }

    private static Simulation simulation() {
        Simulation simulation = new Simulation();
        simulation.setId(21L);
        simulation.setLayoutVersionId(11L);
        simulation.setCreatedBy(7L);
        simulation.setTitle("test simulation");
        simulation.setStatus("DRAFT");
        simulation.setCreatedAt(LocalDateTime.now());
        return simulation;
    }

    private static Simulation parentSimulation(Long layoutVersionId) {
        Simulation simulation = simulation();
        simulation.setId(20L);
        simulation.setLayoutVersionId(layoutVersionId);
        return simulation;
    }

    private static SimulationOption option() {
        SimulationOption option = new SimulationOption();
        option.setSimulationId(21L);
        option.setRandomSeed(1);
        option.setModelProfile("SFM_DEFAULT_V2");
        option.setRoutingProfile("HAZARD_RADIAL_EXP_V3");
        option.setTotalPeople(2);
        option.setWalkingSpeed(BigDecimal.valueOf(1.25));
        option.setReactionTime(BigDecimal.valueOf(0.5));
        option.setInitialResponseTimeStdDev(BigDecimal.ZERO);
        return option;
    }

    private static HazardZone hazard(Long simulationId, double x, double y, double radius) {
        HazardZone hazard = new HazardZone();
        hazard.setSimulationId(simulationId);
        hazard.setCenterX(BigDecimal.valueOf(x));
        hazard.setCenterY(BigDecimal.valueOf(y));
        hazard.setRadius(BigDecimal.valueOf(radius));
        return hazard;
    }

    private static String routeFailureJson(
            String agentId, String currentX, String currentY, String recommendedX, String recommendedY) {
        String recommendation =
                recommendedX == null ? "null" : "{\"x\":" + recommendedX + ",\"y\":" + recommendedY + "}";
        return "{\"code\":\"AGENT_ROUTE_UNREACHABLE\",\"agentId\":"
                + agentId
                + ",\"currentPosition\":{\"x\":"
                + currentX
                + ",\"y\":"
                + currentY
                + "},\"recommendedPosition\":"
                + recommendation
                + "}";
    }

    private static OutsideWall wall(double startX, double startY, double endX, double endY) {
        OutsideWall wall = new OutsideWall();
        wall.setName("outside");
        wall.setStartX(BigDecimal.valueOf(startX));
        wall.setStartY(BigDecimal.valueOf(startY));
        wall.setEndX(BigDecimal.valueOf(endX));
        wall.setEndY(BigDecimal.valueOf(endY));
        return wall;
    }

    private static final class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {}

        @Override
        public void rollback(TransactionStatus status) {}
    }
}

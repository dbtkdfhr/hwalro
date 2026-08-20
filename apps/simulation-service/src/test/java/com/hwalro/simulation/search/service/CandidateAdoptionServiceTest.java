package com.hwalro.simulation.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.LayoutVersion;
import com.hwalro.simulation.drawing.domain.OutsideWall;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.search.domain.CandidateStatus;
import com.hwalro.simulation.search.domain.LayoutSearchCandidateEntity;
import com.hwalro.simulation.search.domain.LayoutSearchEntity;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.PreparedSimulationDto;
import com.hwalro.simulation.search.mapper.LayoutSearchMapper;
import com.hwalro.simulation.simulation.domain.LayoutSimulationContext;
import com.hwalro.simulation.simulation.domain.Simulation;
import com.hwalro.simulation.simulation.domain.SimulationOption;
import com.hwalro.simulation.simulation.exception.InvalidSimulationGeometryException;
import com.hwalro.simulation.simulation.mapper.SimulationMapper;
import com.hwalro.simulation.simulation.service.SimulationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 채택은 구조물을 옮긴 새 배치 버전을 만들면서 원본의 에이전트 좌표를 물려받는다.
 * 그 좌표가 새 구조물 안에 들어가는 경우를 재배치로 해소하는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CandidateAdoptionServiceTest {
    private static final long STUDY_ID = 1L;
    private static final long CANDIDATE_ID = 2L;
    private static final long BASELINE_VERSION_ID = 10L;
    private static final long TARGET_VERSION_ID = 11L;
    private static final long BASELINE_SIMULATION_ID = 20L;
    private static final long FABRIC_ID = 30L;

    @Mock
    private LayoutSearchMapper layoutSearchMapper;

    @Mock
    private DrawingMapper drawingMapper;

    @Mock
    private SimulationMapper simulationMapper;

    @Mock
    private SimulationService simulationService;

    private CandidateAdoptionService service;
    private JwtUser user;

    @BeforeEach
    void setUp() {
        service = new CandidateAdoptionService(
                layoutSearchMapper,
                drawingMapper,
                simulationMapper,
                simulationService,
                new ObjectMapper(),
                new TransactionTemplate(new NoOpTransactionManager()));
        user = new JwtUser(7L, Set.of("OPERATOR"));
    }

    @Test
    void relocatesAgentsThatTheMovedFabricNowCovers() {
        // 원본에서는 (5,5)가 비어 있었지만, 후보는 구조물을 그 위로 옮긴다.
        stubAdoption("[[5,5],[2,2]]", movedFabric(), List.of());

        PreparedSimulationDto prepared = service.prepare(STUDY_ID, CANDIDATE_ID, user);

        assertThat(prepared.simulationId()).isNotNull();
        List<List<BigDecimal>> stored = capturedAgents();
        // 구조물이 덮은 첫 번째 에이전트만 움직이고, 멀리 있던 두 번째는 그대로다.
        double x = stored.get(0).get(0).doubleValue();
        double y = stored.get(0).get(1).doubleValue();
        assertThat(x < 4 || x > 6 || y < 4 || y > 6).isTrue();
        assertThat(stored.get(1).get(0).doubleValue()).isEqualTo(2.0);
        assertThat(stored.get(1).get(1).doubleValue()).isEqualTo(2.0);
    }

    @Test
    void leavesAgentsUntouchedWhenTheMoveDoesNotDisturbThem() {
        stubAdoption("[[2,2],[3,2]]", movedFabric(), List.of());

        service.prepare(STUDY_ID, CANDIDATE_ID, user);

        List<List<BigDecimal>> stored = capturedAgents();
        assertThat(stored.get(0).get(0).doubleValue()).isEqualTo(2.0);
        assertThat(stored.get(0).get(1).doubleValue()).isEqualTo(2.0);
        assertThat(stored.get(1).get(0).doubleValue()).isEqualTo(3.0);
        assertThat(stored.get(1).get(1).doubleValue()).isEqualTo(2.0);
    }

    @Test
    void failsAdoptionWhenAgentsCannotBeRelocated() {
        // 방을 거의 다 채우는 구조물로 옮기면 60명이 설 자리가 없다.
        StringBuilder agents = new StringBuilder("[");
        for (int index = 0; index < 60; index++) {
            agents.append(index == 0 ? "" : ",").append("[4.5,4.5]");
        }
        agents.append("]");
        Fabric huge = fabric(0.5, 0.5, 9.5, 9.0);

        stubAdoption(agents.toString(), huge, List.of());

        assertThatThrownBy(() -> service.prepare(STUDY_ID, CANDIDATE_ID, user))
                .isInstanceOf(InvalidSimulationGeometryException.class);
        verify(simulationMapper, never()).insertInitialState(any(), any());
    }

    /**
     * simulation_options의 초기 반응 시간 두 칼럼은 NOT NULL이다. 복사에서 빠뜨리면 초안 저장이
     * 무결성 위반으로 통째로 롤백되고, 사용자에게는 충돌 메시지만 보인다.
     */
    @Test
    void copiesInitialResponseTimeIntoTheDraftOption() {
        stubAdoption("[[2,2],[3,2]]", movedFabric(), List.of());

        service.prepare(STUDY_ID, CANDIDATE_ID, user);

        ArgumentCaptor<SimulationOption> captor = ArgumentCaptor.forClass(SimulationOption.class);
        verify(simulationMapper).insertSimulationOption(captor.capture());
        SimulationOption copied = captor.getValue();
        assertThat(copied.getInitialResponseTimeMean()).isEqualByComparingTo(BigDecimal.valueOf(5.0));
        assertThat(copied.getInitialResponseTimeStdDev()).isEqualByComparingTo(BigDecimal.valueOf(2.0));
    }

    @Test
    void returnsTheExistingDraftWithoutRelocatingAgain() {
        LayoutSearchEntity study = study();
        LayoutSearchCandidateEntity candidate = candidate();
        candidate.setPreparedSimulationId(99L);
        when(layoutSearchMapper.findSearchById(STUDY_ID)).thenReturn(study);
        when(layoutSearchMapper.findCandidateByIdForUpdate(CANDIDATE_ID)).thenReturn(candidate);
        Simulation existing = new Simulation();
        existing.setId(99L);
        existing.setStatus("DRAFT");
        when(simulationMapper.findSimulationById(99L)).thenReturn(existing);

        PreparedSimulationDto prepared = service.prepare(STUDY_ID, CANDIDATE_ID, user);

        assertThat(prepared.simulationId()).isEqualTo(99L);
        verify(simulationMapper, never()).insertInitialState(any(), any());
        verify(drawingMapper, never()).insertLayoutVersion(any());
    }

    private List<List<BigDecimal>> capturedAgents() {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(simulationMapper).insertInitialState(anyLong(), json.capture());
        try {
            return new ObjectMapper()
                    .readValue(json.getValue(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 원본 도면에는 구조물이 (1,1)-(2,2)에 있고, 후보가 그것을 (4,4)-(6,6)으로 옮긴다. */
    private void stubAdoption(String baselineAgentsJson, Fabric target, List<Fabric> extraFabrics) {
        when(layoutSearchMapper.findSearchById(STUDY_ID)).thenReturn(study());
        when(layoutSearchMapper.findCandidateByIdForUpdate(CANDIDATE_ID)).thenReturn(candidate());

        LayoutVersion sourceVersion = new LayoutVersion();
        sourceVersion.setId(BASELINE_VERSION_ID);
        sourceVersion.setLayoutId(5L);
        when(drawingMapper.findLayoutVersionById(BASELINE_VERSION_ID)).thenReturn(sourceVersion);
        when(drawingMapper.lockLayout(5L)).thenReturn(5L);
        when(drawingMapper.findNextLayoutVersionNumber(5L)).thenReturn(2);
        when(drawingMapper.insertLayoutVersion(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, LayoutVersion.class).setId(TARGET_VERSION_ID);
            return 1;
        });

        Fabric original = fabric(1, 1, 2, 2);
        original.setId(FABRIC_ID);
        when(drawingMapper.findFabricsByVersionId(BASELINE_VERSION_ID)).thenReturn(List.of(original));
        when(drawingMapper.findLayoutExitsByVersionId(anyLong())).thenReturn(List.of());

        // 채택 후 새 버전에서 조회되는 도면 - 구조물이 옮겨진 상태다.
        java.util.List<Fabric> targetFabrics = new java.util.ArrayList<>(extraFabrics);
        targetFabrics.add(target);
        when(drawingMapper.findFabricsByVersionId(TARGET_VERSION_ID)).thenReturn(targetFabrics);
        when(drawingMapper.findWallsByVersionId(TARGET_VERSION_ID)).thenReturn(List.of());
        when(drawingMapper.findPillarsByVersionId(TARGET_VERSION_ID)).thenReturn(List.of());
        when(drawingMapper.findOutsideWallsByVersionId(TARGET_VERSION_ID)).thenReturn(squareOutsideWalls());

        LayoutSimulationContext context = new LayoutSimulationContext();
        context.setWidth(BigDecimal.valueOf(10));
        context.setHeight(BigDecimal.valueOf(10));
        when(simulationMapper.findLayoutContext(TARGET_VERSION_ID)).thenReturn(context);

        Simulation baseline = new Simulation();
        baseline.setId(BASELINE_SIMULATION_ID);
        baseline.setLayoutVersionId(BASELINE_VERSION_ID);
        when(simulationMapper.findSimulationById(BASELINE_SIMULATION_ID)).thenReturn(baseline);

        SimulationOption option = new SimulationOption();
        option.setTotalPeople(2);
        option.setWalkingSpeed(BigDecimal.valueOf(1.2));
        option.setReactionTime(BigDecimal.valueOf(0.5));
        option.setInitialResponseTimeMean(BigDecimal.valueOf(5.0));
        option.setInitialResponseTimeStdDev(BigDecimal.valueOf(2.0));
        when(simulationMapper.findSimulationOption(BASELINE_SIMULATION_ID)).thenReturn(option);
        when(simulationMapper.findInitialStateJson(BASELINE_SIMULATION_ID)).thenReturn(baselineAgentsJson);
        when(simulationMapper.findHazardZones(BASELINE_SIMULATION_ID)).thenReturn(List.of());
        when(simulationMapper.findSelectedExitIds(BASELINE_SIMULATION_ID)).thenReturn(List.of());
        when(simulationMapper.insertSimulation(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, Simulation.class).setId(21L);
            return 1;
        });
    }

    private static LayoutSearchEntity study() {
        LayoutSearchEntity study = new LayoutSearchEntity();
        study.setId(STUDY_ID);
        study.setBaselineSimulationId(BASELINE_SIMULATION_ID);
        study.setBaselineLayoutVersionId(BASELINE_VERSION_ID);
        return study;
    }

    private static LayoutSearchCandidateEntity candidate() {
        LayoutSearchCandidateEntity candidate = new LayoutSearchCandidateEntity();
        candidate.setId(CANDIDATE_ID);
        candidate.setStudyId(STUDY_ID);
        candidate.setStatus(CandidateStatus.EVALUATED.name());
        candidate.setChangeSet("{\"schemaVersion\":1,\"coordinateUnit\":\"METER\",\"ops\":[{\"type\":\"MOVE_FABRIC\","
                + "\"fabricId\":" + FABRIC_ID + ","
                + "\"before\":{\"startX\":1,\"startY\":1,\"endX\":2,\"endY\":2,\"rotation\":0},"
                + "\"after\":{\"startX\":4,\"startY\":4,\"endX\":6,\"endY\":6,\"rotation\":0}}]}");
        return candidate;
    }

    private static List<OutsideWall> squareOutsideWalls() {
        return List.of(
                outsideWall(0, 0, 10, 0), outsideWall(10, 0, 10, 10),
                outsideWall(10, 10, 0, 10), outsideWall(0, 10, 0, 0));
    }

    private static OutsideWall outsideWall(double startX, double startY, double endX, double endY) {
        OutsideWall wall = new OutsideWall();
        wall.setName("outside");
        wall.setStartX(BigDecimal.valueOf(startX));
        wall.setStartY(BigDecimal.valueOf(startY));
        wall.setEndX(BigDecimal.valueOf(endX));
        wall.setEndY(BigDecimal.valueOf(endY));
        return wall;
    }

    private static Fabric movedFabric() {
        return fabric(4, 4, 6, 6);
    }

    private static Fabric fabric(double startX, double startY, double endX, double endY) {
        Fabric fabric = new Fabric();
        fabric.setName("fabric");
        fabric.setStartX(BigDecimal.valueOf(startX));
        fabric.setStartY(BigDecimal.valueOf(startY));
        fabric.setEndX(BigDecimal.valueOf(endX));
        fabric.setEndY(BigDecimal.valueOf(endY));
        fabric.setRotation(BigDecimal.ZERO);
        return fabric;
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

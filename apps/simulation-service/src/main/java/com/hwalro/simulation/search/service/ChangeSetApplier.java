package com.hwalro.simulation.search.service;

import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.LayoutExit;
import com.hwalro.simulation.drawing.domain.Pillar;
import com.hwalro.simulation.drawing.domain.Wall;
import com.hwalro.simulation.search.domain.ChangeOp;
import com.hwalro.simulation.search.domain.ChangeSet;
import com.hwalro.simulation.simulation.dto.SimulationDtos.DrawingGeometryDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.ExitDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.FabricRectDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.RectDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SegmentDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationSetupResponse;
import com.hwalro.simulation.simulation.service.SimulationGeometry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class ChangeSetApplier {
    private static final String MOVE_FABRIC = "MOVE_FABRIC";

    public SimulationSetupResponse apply(SimulationSetupResponse baseline, ChangeSet changeSet) {
        List<FabricRectDto> fabrics = new ArrayList<>(baseline.drawing().fabrics());
        for (ChangeOp op : changeSet.ops()) {
            if (!MOVE_FABRIC.equals(op.type())) {
                throw new IllegalArgumentException("지원하지 않는 변경 연산입니다: " + op.type());
            }
            if (op.fabricId() == null || op.before() == null || op.after() == null) {
                throw new IllegalArgumentException("MOVE_FABRIC에는 fabricId, before, after가 모두 필요합니다.");
            }
            validateTransform(op.before(), op.fabricId());
            validateTransform(op.after(), op.fabricId());
            int index = indexOfFabric(fabrics, op);
            FabricRectDto current = fabrics.get(index);
            if (!sameTransform(current, op.before())) {
                throw new IllegalArgumentException("변경 연산의 before 연결이 올바르지 않습니다: fabricId=" + op.fabricId());
            }
            fabrics.set(
                    index,
                    new FabricRectDto(
                            current.id(),
                            current.name(),
                            op.after().startX(),
                            op.after().startY(),
                            op.after().endX(),
                            op.after().endY(),
                            op.after().rotation()));
        }
        DrawingGeometryDto drawing = baseline.drawing();
        DrawingGeometryDto mutatedDrawing = new DrawingGeometryDto(
                drawing.layoutId(),
                drawing.title(),
                drawing.width(),
                drawing.height(),
                drawing.outsideBoundary(),
                drawing.walls(),
                drawing.pillars(),
                List.copyOf(fabrics),
                drawing.layoutTexts(),
                drawing.exits());
        // 구조물이 움직인 배치에 원본 좌표를 그대로 쓰면 사람이 집기 안에서 출발해 엔진이 기하 구성
        // 단계에서 죽는다. 채택 경로(CandidateAdoptionService)와 같은 재배치를 여기서도 적용한다.
        List<PointDto> relaxedAgents = relaxAgents(baseline, mutatedDrawing);
        return new SimulationSetupResponse(
                baseline.simulationId(),
                baseline.layoutVersionId(),
                baseline.parentSimulationId(),
                baseline.title(),
                baseline.status(),
                baseline.createdAt(),
                baseline.randomSeed(),
                baseline.modelProfile(),
                baseline.routingProfile(),
                baseline.totalPeople(),
                baseline.walkingSpeed(),
                baseline.initialResponseTimeStdDev(),
                relaxedAgents,
                baseline.hazardZones(),
                baseline.selectedExitIds(),
                mutatedDrawing);
    }

    /**
     * 변경된 배치를 기준으로 에이전트를 밀어내고, 결과를 엔진에 넘기기 전에 다시 확인한다.
     *
     * <p>재배치에 실패하면 그 후보는 사람이 설 자리가 없는 배치라는 뜻이므로, 예외를 그대로 올려
     * 호출부가 시행을 실패로 기록하게 둔다. 원본 좌표로 되돌려 실행하면 엔진이 어차피 죽는다.
     */
    private List<PointDto> relaxAgents(SimulationSetupResponse baseline, DrawingGeometryDto drawing) {
        List<Wall> walls =
                drawing.walls().stream().map(ChangeSetApplier::toWall).toList();
        List<Pillar> pillars =
                drawing.pillars().stream().map(ChangeSetApplier::toPillar).toList();
        List<Fabric> fabrics =
                drawing.fabrics().stream().map(ChangeSetApplier::toFabric).toList();
        List<LayoutExit> exits =
                drawing.exits().stream().map(ChangeSetApplier::toExit).toList();
        List<PointDto> boundary = drawing.outsideBoundary();

        List<PointDto> relaxed =
                SimulationGeometry.relaxAgents(baseline.agentPositions(), boundary, walls, pillars, fabrics, exits);
        SimulationGeometry.validateSetup(relaxed, baseline.hazardZones(), boundary, walls, pillars, fabrics, exits);
        return relaxed;
    }

    private static Wall toWall(SegmentDto segment) {
        Wall wall = new Wall();
        wall.setName(segment.name());
        wall.setStartX(segment.startX());
        wall.setStartY(segment.startY());
        wall.setEndX(segment.endX());
        wall.setEndY(segment.endY());
        return wall;
    }

    private static Pillar toPillar(RectDto rect) {
        Pillar pillar = new Pillar();
        pillar.setName(rect.name());
        pillar.setStartX(rect.startX());
        pillar.setStartY(rect.startY());
        pillar.setEndX(rect.endX());
        pillar.setEndY(rect.endY());
        pillar.setRotation(rect.rotation());
        return pillar;
    }

    private static Fabric toFabric(FabricRectDto rect) {
        Fabric fabric = new Fabric();
        fabric.setId(rect.id());
        fabric.setName(rect.name());
        fabric.setStartX(rect.startX());
        fabric.setStartY(rect.startY());
        fabric.setEndX(rect.endX());
        fabric.setEndY(rect.endY());
        fabric.setRotation(rect.rotation());
        return fabric;
    }

    private static LayoutExit toExit(ExitDto exit) {
        LayoutExit layoutExit = new LayoutExit();
        layoutExit.setId(exit.id());
        layoutExit.setName(exit.name());
        layoutExit.setStartX(exit.startX());
        layoutExit.setStartY(exit.startY());
        layoutExit.setEndX(exit.endX());
        layoutExit.setEndY(exit.endY());
        return layoutExit;
    }

    private int indexOfFabric(List<FabricRectDto> fabrics, ChangeOp op) {
        for (int index = 0; index < fabrics.size(); index++) {
            if (op.fabricId().equals(fabrics.get(index).id())) {
                return index;
            }
        }
        throw new IllegalArgumentException("변경 대상 fabric을 찾을 수 없습니다: fabricId=" + op.fabricId());
    }

    private void validateTransform(ChangeOp.FabricTransform transform, Long fabricId) {
        if (transform.startX() == null
                || transform.startY() == null
                || transform.endX() == null
                || transform.endY() == null
                || transform.rotation() == null
                || transform.startX().compareTo(transform.endX()) >= 0
                || transform.startY().compareTo(transform.endY()) >= 0) {
            throw new IllegalArgumentException("변경 좌표가 올바르지 않습니다: fabricId=" + fabricId);
        }
    }

    private boolean sameTransform(FabricRectDto rect, ChangeOp.FabricTransform transform) {
        return equals(rect.startX(), transform.startX())
                && equals(rect.startY(), transform.startY())
                && equals(rect.endX(), transform.endX())
                && equals(rect.endY(), transform.endY())
                && equals(rect.rotation(), transform.rotation());
    }

    private boolean equals(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) == 0;
    }
}

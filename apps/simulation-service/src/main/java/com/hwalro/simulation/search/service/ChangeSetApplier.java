package com.hwalro.simulation.search.service;

import com.hwalro.simulation.search.domain.ChangeOp;
import com.hwalro.simulation.search.domain.ChangeSet;
import com.hwalro.simulation.simulation.dto.SimulationDtos.DrawingGeometryDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.FabricRectDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationSetupResponse;
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
                baseline.initialResponseTimeMean(),
                baseline.initialResponseTimeStdDev(),
                baseline.agentPositions(),
                baseline.hazardZones(),
                baseline.selectedExitIds(),
                mutatedDrawing);
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

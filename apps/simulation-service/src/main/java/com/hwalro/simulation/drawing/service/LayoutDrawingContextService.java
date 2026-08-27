package com.hwalro.simulation.drawing.service;

import com.hwalro.simulation.common.jwt.ForbiddenException;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.drawing.domain.OutsideWall;
import com.hwalro.simulation.drawing.dto.DrawingResponse;
import com.hwalro.simulation.drawing.dto.ExitDto;
import com.hwalro.simulation.drawing.dto.FabricDto;
import com.hwalro.simulation.drawing.dto.LayoutDrawingContextResponse;
import com.hwalro.simulation.drawing.dto.LayoutDrawingContextResponse.Drawing;
import com.hwalro.simulation.drawing.dto.LayoutDrawingContextResponse.LayoutText;
import com.hwalro.simulation.drawing.dto.LayoutDrawingContextResponse.Point;
import com.hwalro.simulation.drawing.dto.LayoutDrawingContextResponse.Rectangle;
import com.hwalro.simulation.drawing.dto.LayoutDrawingContextResponse.Segment;
import com.hwalro.simulation.drawing.dto.OutsideWallDto;
import com.hwalro.simulation.drawing.dto.WallDto;
import com.hwalro.simulation.simulation.exception.InvalidSimulationGeometryException;
import com.hwalro.simulation.simulation.service.SimulationGeometry;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class LayoutDrawingContextService {
    private static final int MAX_LAYOUT_COUNT = 20;

    private final DrawingService drawingService;

    public LayoutDrawingContextService(DrawingService drawingService) {
        this.drawingService = drawingService;
    }

    public List<LayoutDrawingContextResponse> findAll(List<Long> layoutIds, JwtUser user) {
        List<Long> validatedIds = validate(layoutIds);
        return validatedIds.stream()
                .flatMap(layoutId -> findContext(layoutId, user))
                .toList();
    }

    private Stream<LayoutDrawingContextResponse> findContext(Long layoutId, JwtUser user) {
        try {
            return Stream.of(toContext(drawingService.get(layoutId, user)));
        } catch (ForbiddenException exception) {
            if (DrawingService.isPrivileged(user)) {
                throw exception;
            }
            return Stream.empty();
        }
    }

    private LayoutDrawingContextResponse toContext(DrawingResponse drawing) {
        List<Point> outsideBoundary = toBoundary(drawing);
        return new LayoutDrawingContextResponse(
                drawing.id(),
                drawing.layoutVersionId(),
                drawing.layoutVersionNumber(),
                drawing.title(),
                new Drawing(
                        drawing.title(),
                        drawing.width().doubleValue(),
                        drawing.height().doubleValue(),
                        outsideBoundary,
                        drawing.walls().stream().map(this::toSegment).toList(),
                        drawing.exits().stream().map(this::toSegment).toList(),
                        drawing.pillars().stream().map(this::toRectangle).toList(),
                        drawing.fabrics().stream().map(this::toRectangle).toList(),
                        drawing.layoutTexts().stream()
                                .map(text -> new LayoutText(
                                        text.text(),
                                        text.x().doubleValue(),
                                        text.y().doubleValue()))
                                .toList()));
    }

    private List<Point> toBoundary(DrawingResponse drawing) {
        try {
            return SimulationGeometry.assembleBoundary(
                            toOutsideWalls(drawing.outsideWalls()), drawing.width(), drawing.height())
                    .stream()
                    .map(point -> new Point(point.x().doubleValue(), point.y().doubleValue()))
                    .toList();
        } catch (InvalidSimulationGeometryException exception) {
            // 미리보기·스냅샷 용도라 폐곡선이 아니어도 나머지 지오메트리는 반환한다.
            return List.of();
        }
    }

    private List<OutsideWall> toOutsideWalls(List<OutsideWallDto> outsideWalls) {
        return outsideWalls.stream()
                .map(wall -> {
                    OutsideWall entity = new OutsideWall();
                    entity.setName(wall.name());
                    entity.setStartX(wall.startX());
                    entity.setStartY(wall.startY());
                    entity.setEndX(wall.endX());
                    entity.setEndY(wall.endY());
                    return entity;
                })
                .toList();
    }

    private Segment toSegment(WallDto wall) {
        return new Segment(
                wall.name(),
                wall.startX().doubleValue(),
                wall.startY().doubleValue(),
                wall.endX().doubleValue(),
                wall.endY().doubleValue());
    }

    private Segment toSegment(ExitDto exit) {
        return new Segment(
                exit.name(),
                exit.startX().doubleValue(),
                exit.startY().doubleValue(),
                exit.endX().doubleValue(),
                exit.endY().doubleValue());
    }

    private Rectangle toRectangle(com.hwalro.simulation.drawing.dto.PillarDto pillar) {
        return toRectangle(
                pillar.name(), pillar.startX(), pillar.startY(), pillar.endX(), pillar.endY(), pillar.rotation());
    }

    private Rectangle toRectangle(FabricDto fabric) {
        return toRectangle(
                fabric.name(), fabric.startX(), fabric.startY(), fabric.endX(), fabric.endY(), fabric.rotation());
    }

    private Rectangle toRectangle(
            String name, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY, BigDecimal rotation) {
        return new Rectangle(
                name,
                startX.doubleValue(),
                startY.doubleValue(),
                endX.doubleValue(),
                endY.doubleValue(),
                rotation == null ? 0 : rotation.doubleValue());
    }

    private List<Long> validate(List<Long> layoutIds) {
        if (layoutIds == null || layoutIds.isEmpty() || layoutIds.size() > MAX_LAYOUT_COUNT) {
            throw new IllegalArgumentException("도면 ID는 1개 이상 " + MAX_LAYOUT_COUNT + "개 이하여야 합니다.");
        }
        Set<Long> uniqueIds = new HashSet<>();
        for (Long id : layoutIds) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("도면 ID는 양수여야 합니다.");
            }
            if (!uniqueIds.add(id)) {
                throw new IllegalArgumentException("도면 ID는 중복될 수 없습니다.");
            }
        }
        return List.copyOf(layoutIds);
    }
}

package com.hwalro.simulation.drawing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class DefaultDrawingData {

    public record DefaultDrawing(
            String name,
            BigDecimal width,
            BigDecimal height,
            List<DefaultWall> walls,
            List<DefaultOutsideWall> outsideWalls,
            List<DefaultPillar> pillars,
            List<DefaultFabric> fabrics,
            List<DefaultExit> exits,
            List<DefaultLayoutText> layoutTexts,
            List<DefaultZone> zones) {}

    public record DefaultWall(
            String name,
            BigDecimal startX,
            BigDecimal startY,
            BigDecimal endX,
            BigDecimal endY,
            Integer displayOrder) {}

    public record DefaultOutsideWall(
            String name, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY) {}

    public record DefaultPillar(
            String name,
            BigDecimal startX,
            BigDecimal startY,
            BigDecimal endX,
            BigDecimal endY,
            BigDecimal rotation,
            Integer displayOrder) {}

    public record DefaultFabric(
            String name,
            BigDecimal startX,
            BigDecimal startY,
            BigDecimal endX,
            BigDecimal endY,
            BigDecimal rotation,
            Integer displayOrder) {}

    public record DefaultExit(String name, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY) {}

    public record DefaultLayoutText(String text, BigDecimal x, BigDecimal y) {}

    /**
     * 기본 도면에 딸려 오는 구역.
     *
     * <p>참조는 ID가 아니라 <b>배열 인덱스</b>다. 기본 도면으로 새 도면을 만들면 요소는 새 ID를 받으므로 ID를 적어 둘 수 없다. 담당 직원 배정은 담지 않는다 —
     * 사용자 ID는 환경마다 다르다.
     *
     * @param defaultExitIndex {@code exits} 배열의 인덱스. 지정하지 않았으면 null.
     */
    public record DefaultZone(
            String name,
            String zoneType,
            BigDecimal x,
            BigDecimal y,
            BigDecimal width,
            BigDecimal height,
            Integer displayOrder,
            Integer defaultExitIndex,
            List<DefaultZoneMember> members) {}

    /**
     * 구역에 속한 요소 하나.
     *
     * @param kind WALL·PILLAR·FABRIC. 세 종류의 ID가 서로 겹치므로 종류 없이는 요소를 특정할 수 없다.
     * @param index 그 종류 배열에서의 인덱스.
     */
    public record DefaultZoneMember(String kind, int index) {}

    private final DefaultDrawing defaultDrawing;

    public DefaultDrawingData(ObjectMapper objectMapper) {
        try (InputStream inputStream = new ClassPathResource("drawings/default-drawing-v10.json").getInputStream()) {
            this.defaultDrawing = objectMapper.readValue(inputStream, DefaultDrawing.class);
        } catch (IOException e) {
            throw new IllegalStateException("기본 도면 데이터를 불러올 수 없습니다.", e);
        }
    }

    public DefaultDrawing get() {
        return defaultDrawing;
    }
}

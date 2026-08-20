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
            List<DefaultLayoutText> layoutTexts) {}

    public record DefaultWall(String name, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY) {}

    public record DefaultOutsideWall(
            String name, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY) {}

    public record DefaultPillar(
            String name, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY, BigDecimal rotation) {}

    public record DefaultFabric(
            String name, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY, BigDecimal rotation) {}

    public record DefaultExit(String name, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY) {}

    public record DefaultLayoutText(String text, BigDecimal x, BigDecimal y) {}

    private final DefaultDrawing defaultDrawing;

    public DefaultDrawingData(ObjectMapper objectMapper) {
        try (InputStream inputStream = new ClassPathResource("drawings/default-drawing-v7.json").getInputStream()) {
            this.defaultDrawing = objectMapper.readValue(inputStream, DefaultDrawing.class);
        } catch (IOException e) {
            throw new IllegalStateException("기본 도면 데이터를 불러올 수 없습니다.", e);
        }
    }

    public DefaultDrawing get() {
        return defaultDrawing;
    }
}

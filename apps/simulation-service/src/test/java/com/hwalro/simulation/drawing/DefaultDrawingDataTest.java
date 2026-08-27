package com.hwalro.simulation.drawing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.drawing.DefaultDrawingData.DefaultDrawing;
import com.hwalro.simulation.drawing.DefaultDrawingData.DefaultZone;
import com.hwalro.simulation.drawing.DefaultDrawingData.DefaultZoneMember;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 기본 도면 데이터 파일 자체를 검사한다.
 *
 * <p>구역 소속은 요소를 배열 인덱스로 가리킨다. 인덱스가 범위를 벗어나면 도면 생성이 통째로 실패하고, 종류를 헷갈리면 소속이 조용히 엉뚱한 요소에 붙는다. 파일을 다시
 * 내보낼 때 그런 사고를 여기서 잡는다.
 */
class DefaultDrawingDataTest {
    private final DefaultDrawing drawing = new DefaultDrawingData(new ObjectMapper()).get();

    @Test
    void everyZoneMemberPointsAtAnExistingElementOfItsOwnKind() {
        Map<String, Integer> sizes = Map.of(
                "WALL", drawing.walls().size(),
                "PILLAR", drawing.pillars().size(),
                "FABRIC", drawing.fabrics().size());

        assertThat(drawing.zones()).isNotEmpty();
        for (DefaultZone zone : drawing.zones()) {
            for (DefaultZoneMember member : zone.members()) {
                assertThat(sizes).as("구역 \"%s\"의 소속 종류", zone.name()).containsKey(member.kind());
                assertThat(member.index())
                        .as("구역 \"%s\"의 %s 인덱스", zone.name(), member.kind())
                        .isGreaterThanOrEqualTo(0)
                        .isLessThan(sizes.get(member.kind()));
            }
        }
    }

    @Test
    void noElementBelongsToTwoZones() {
        List<String> keys = drawing.zones().stream()
                .flatMap(zone -> zone.members().stream())
                .map(member -> member.kind() + "#" + member.index())
                .toList();

        assertThat(keys).doesNotHaveDuplicates();
    }

    @Test
    void everyDefaultExitIndexPointsAtAnExistingExit() {
        for (DefaultZone zone : drawing.zones()) {
            if (zone.defaultExitIndex() == null) {
                continue;
            }
            assertThat(zone.defaultExitIndex())
                    .as("구역 \"%s\"의 기본 비상구 인덱스", zone.name())
                    .isGreaterThanOrEqualTo(0)
                    .isLessThan(drawing.exits().size());
        }
    }

    /** display_order는 종류별 인덱스가 아니라 도면 전체의 단일 순서다. 빠지면 캔버스 겹침 순서가 검수한 모습과 달라진다. */
    @Test
    void everyStackedElementCarriesItsDisplayOrder() {
        assertThat(drawing.walls())
                .allSatisfy(wall -> assertThat(wall.displayOrder()).isNotNull());
        assertThat(drawing.pillars())
                .allSatisfy(pillar -> assertThat(pillar.displayOrder()).isNotNull());
        assertThat(drawing.fabrics())
                .allSatisfy(fabric -> assertThat(fabric.displayOrder()).isNotNull());
    }
}

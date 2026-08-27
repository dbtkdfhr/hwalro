package com.hwalro.simulation.zone.service;

import com.hwalro.simulation.zone.domain.LayoutZone;
import com.hwalro.simulation.zone.dto.EvacuationRouteResponse;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import org.springframework.stereotype.Component;

/**
 * 도면 전체 대피 경로 결과를 담아 두는 메모리 캐시.
 *
 * <p>한 번 계산하는 데 파이썬 엔진 프로세스가 뜨므로 수 초가 걸린다. 사용자가 편집기에서 대피 동선 토글을 껐다 켜거나
 * 화면을 다시 열 때마다 같은 계산을 반복할 이유가 없다.
 *
 * <h2>키를 버전 ID만으로 두지 않는 이유</h2>
 *
 * <p>도면 기하는 저장할 때마다 새 버전이 생기지만 <b>구역은 그렇지 않다</b>. {@code LayoutZoneService}의 구역
 * 생성·수정·삭제는 현재 버전을 제자리에서 고친다. 버전 ID만 키로 쓰면 구역을 옮기거나 담당 비상구를 바꿔도 낡은 경로가
 * 계속 나온다. 그래서 경로 결과를 좌우하는 구역 값(ID·사각형·기본 비상구)의 지문을 키에 함께 넣는다.
 *
 * <h2>크기와 수명</h2>
 *
 * <ul>
 *   <li>최대 {@value #MAX_ENTRIES}개 항목을 들고 가장 오래 쓰지 않은 것부터 버린다.
 *   <li>만료 시간을 두지 않는다. 키가 내용을 담고 있어 입력이 바뀌면 키가 바뀌고, 옛 항목은 LRU로 밀려난다.
 *   <li>애플리케이션 메모리에만 산다. 재시작하면 사라지며 그래도 정확성에는 영향이 없다.
 *   <li>따라서 <b>무효화를 호출하는 쪽이 따로 없다</b>. 구역이나 도면을 고치는 코드가 캐시를 알 필요가 없다.
 * </ul>
 *
 * <h2>담지 않는 것</h2>
 *
 * <p>실패는 캐시하지 않는다. 엔진이 일시적으로 죽은 상태를 오래 붙들고 있으면 복구된 뒤에도 계속 실패를 돌려준다.
 *
 * <p>사용자별로 걸러낸 결과도 담지 않는다. 담는 것은 항상 <b>전체 구역 결과</b>이며, 보는 사람에 따른 필터는 캐시에서 꺼낸
 * 뒤에 적용해야 한다. 필터링된 결과를 캐시하면 다음 사람이 남의 구역을 보게 된다.
 */
@Component
public class EvacuationRouteCache {
    /** 표시 경로 알고리즘이 바뀌면 저장된 이전 계산 결과를 재사용하지 않도록 올린다. */
    static final String ROUTE_ALGORITHM_VERSION = "natural-exit-approach-v3";

    /** 동시에 검토할 만한 도면 수를 넉넉히 덮는 크기. 항목 하나는 구역 수십 개 분량의 경로다. */
    static final int MAX_ENTRIES = 16;

    private final Map<String, List<EvacuationRouteResponse>> entries = new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<EvacuationRouteResponse>> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    /** 캐시된 전체 구역 결과. 없으면 null. */
    public synchronized List<EvacuationRouteResponse> find(String key) {
        return entries.get(key);
    }

    public synchronized void put(String key, List<EvacuationRouteResponse> routes) {
        entries.put(key, List.copyOf(routes));
    }

    /**
     * 경로 결과를 좌우하는 입력만 모은 키.
     *
     * <p>구역의 이름이나 담당자는 경로를 바꾸지 않으므로 넣지 않는다. 넣으면 이름만 고쳐도 엔진이 다시 돈다.
     */
    public static String keyOf(Long layoutVersionId, List<LayoutZone> zones) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(ROUTE_ALGORITHM_VERSION);
        joiner.add(String.valueOf(layoutVersionId));
        zones.stream()
                .sorted((left, right) -> Long.compare(idOf(left), idOf(right)))
                .forEach(zone -> joiner.add(fingerprintOf(zone)));
        return joiner.toString();
    }

    private static long idOf(LayoutZone zone) {
        return zone.getId() == null ? -1L : zone.getId();
    }

    private static String fingerprintOf(LayoutZone zone) {
        return new StringJoiner(",")
                .add(String.valueOf(idOf(zone)))
                .add(plain(zone.getX()))
                .add(plain(zone.getY()))
                .add(plain(zone.getWidth()))
                .add(plain(zone.getHeight()))
                .add(String.valueOf(zone.getDefaultExitId()))
                .toString();
    }

    /** 같은 수를 다르게 적은 표기(10 과 10.00)가 다른 키가 되지 않게 정규화한다. */
    private static String plain(BigDecimal value) {
        return value == null
                ? "null"
                : Objects.toString(value.stripTrailingZeros().toPlainString());
    }
}

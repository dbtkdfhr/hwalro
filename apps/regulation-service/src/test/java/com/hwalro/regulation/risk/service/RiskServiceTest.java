package com.hwalro.regulation.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hwalro.regulation.common.jwt.JwtUser;
import com.hwalro.regulation.report.client.AuthorDirectoryClient;
import com.hwalro.regulation.report.exception.SimulationServiceException;
import com.hwalro.regulation.risk.client.RiskDrawingContextClient;
import com.hwalro.regulation.risk.domain.Risk;
import com.hwalro.regulation.risk.dto.LayoutDrawingContextResponse;
import com.hwalro.regulation.risk.dto.RiskCreateRequest;
import com.hwalro.regulation.risk.dto.RiskResponse;
import com.hwalro.regulation.risk.mapper.RiskMapper;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiskServiceTest {
    @Mock
    private RiskMapper riskMapper;

    @Mock
    private RiskDrawingContextClient drawingContextClient;

    @Mock
    private AuthorDirectoryClient authorDirectoryClient;

    @Test
    void createUsesCurrentLayoutVersion() {
        AtomicReference<Risk> stored = new AtomicReference<>();
        when(drawingContextClient.findLayoutContexts(List.of(10L), "Bearer token"))
                .thenReturn(List.of(layoutContext()));
        when(riskMapper.insert(any())).thenAnswer(invocation -> {
            Risk risk = invocation.getArgument(0);
            risk.setId(1L);
            stored.set(risk);
            return 1;
        });
        when(riskMapper.findById(1L)).thenAnswer(invocation -> stored.get());

        RiskResponse response = service().create(request(null), 7L, "Bearer token");

        assertThat(stored.get().getLayoutVersionId()).isEqualTo(101L);
        assertThat(response.layoutVersionId()).isEqualTo(101L);
        assertThat(response.layoutTitle()).isEqualTo("현재 배치안");
    }

    @Test
    void createRejectsDifferentOrMissingLayoutContext() {
        when(drawingContextClient.findLayoutContexts(List.of(10L), "Bearer token"))
                .thenReturn(List.of(layoutContext()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service().create(request(99L), 7L, "Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("도면 버전 ID가 현재 도면 버전과 일치하지 않습니다.");
        assertThatThrownBy(() -> service().create(request(null), 7L, "Bearer token"))
                .isInstanceOf(SimulationServiceException.class)
                .hasMessage("도면을 조회할 수 없습니다.");
        verify(riskMapper, never()).insert(any());
    }

    @Test
    void getEnrichesLayoutTitleAfterAuthorization() {
        Risk risk = new Risk();
        risk.setId(1L);
        risk.setLayoutId(10L);
        risk.setAssigneeId(7L);
        when(riskMapper.findById(1L)).thenReturn(risk);
        when(drawingContextClient.findLayoutContexts(List.of(10L), "Bearer token"))
                .thenReturn(List.of(layoutContext()));

        RiskResponse response = service().get(1L, new JwtUser(7L, Set.of("OPERATOR")), "Bearer token");

        assertThat(response.layoutTitle()).isEqualTo("현재 배치안");
    }

    private RiskService service() {
        return new RiskService(riskMapper, drawingContextClient, authorDirectoryClient);
    }

    private RiskCreateRequest request(Long layoutVersionId) {
        return new RiskCreateRequest(
                10L, layoutVersionId, null, null, null, null, "위험 구역", "설명", "높음", "임시저장", List.of());
    }

    private LayoutDrawingContextResponse layoutContext() {
        return new LayoutDrawingContextResponse(10L, 101L, 3, "현재 배치안", null);
    }
}

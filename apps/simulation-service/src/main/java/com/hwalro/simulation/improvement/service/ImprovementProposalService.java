package com.hwalro.simulation.improvement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.improvement.domain.FabricChange;
import com.hwalro.simulation.improvement.domain.ImprovementProposal;
import com.hwalro.simulation.improvement.domain.ProposalCandidate;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import com.hwalro.simulation.improvement.mapper.ImprovementProposalMapper;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 개선 후보를 화면·후속 시뮬레이션에 사용할 영속 개선안으로 저장합니다. */
@Service
public class ImprovementProposalService {
    private static final List<String> PROPOSAL_TYPES = List.of("MINIMAL", "BALANCED", "MAXIMUM");

    private final ImprovementProposalMapper improvementProposalMapper;
    private final ObjectMapper objectMapper;

    public ImprovementProposalService(ImprovementProposalMapper improvementProposalMapper, ObjectMapper objectMapper) {
        this.improvementProposalMapper = improvementProposalMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 저장되지 않은 제안만 현재 탐색 결과로 교체합니다.
     *
     * <p>한 건이라도 저장됐다면 사용자가 확정한 이력이므로 재생성하지 않습니다.
     */
    @Transactional
    public List<ImprovementProposal> replaceUnsaved(long sourceSimulationId, List<ProposalCandidate> candidates) {
        if (candidates.size() > PROPOSAL_TYPES.size()) {
            throw new IllegalArgumentException("개선안은 최대 3개까지만 저장할 수 있습니다.");
        }
        improvementProposalMapper.lockSourceSimulationId(sourceSimulationId);
        if (improvementProposalMapper.existsSavedBySourceSimulationId(sourceSimulationId)) {
            throw new IllegalStateException("저장된 개선안이 있어 재생성할 수 없습니다.");
        }

        improvementProposalMapper.deleteUnsavedBySourceSimulationId(sourceSimulationId);
        List<ImprovementProposal> proposals = IntStream.range(0, candidates.size())
                .mapToObj(index -> toProposal(sourceSimulationId, candidates.get(index), index))
                .toList();
        proposals.forEach(improvementProposalMapper::insert);
        return proposals;
    }

    private ImprovementProposal toProposal(long sourceSimulationId, ProposalCandidate candidate, int index) {
        String summary = "병목 구역의 보행 폭 확보를 위해 fabric " + candidate.changes().size() + "개를 변경합니다.";
        ImprovementProposal proposal = new ImprovementProposal();
        proposal.setSourceSimulationId(sourceSimulationId);
        proposal.setProposalOrder(index + 1);
        proposal.setProposalType(PROPOSAL_TYPES.get(index));
        proposal.setTitle("배치 개선안 " + (index + 1));
        proposal.setDescription(summary);
        proposal.setChangeData(write(new ChangeData(
                1,
                "FLOOR_PLAN",
                "METER",
                candidate.changes().stream().map(this::toOperation).toList())));
        proposal.setChangeSummary(write(new ChangeSummary(
                1,
                summary,
                candidate.changes().stream().map(this::toHighlight).toList(),
                List.of(new RiskInterpretation("ROUTE_BOTTLENECK", "원본 시뮬레이션에서 병목 구역이 확인되었습니다.")),
                new VerificationGuide(true, "변경된 배치 버전으로 시뮬레이션을 실행해 공식 지표 변화를 확인해야 합니다."))));
        return proposal;
    }

    private ChangeOperation toOperation(FabricChange change) {
        return new ChangeOperation(
                "MOVE_FACILITY",
                "FABRIC",
                change.fabricId(),
                toRectangle(change.before()),
                toRectangle(change.after()),
                "병목 구역의 보행 폭을 확보합니다.");
    }

    private ChangeHighlight toHighlight(FabricChange change) {
        return new ChangeHighlight("fabric " + change.fabricId() + " 변경", "병목 구역의 보행 폭 확보를 위해 위치 또는 회전을 변경합니다.");
    }

    private RectangleData toRectangle(RotatedRectangle rectangle) {
        double halfWidth = rectangle.width() / 2;
        double halfHeight = rectangle.height() / 2;
        return new RectangleData(
                rectangle.center().x() - halfWidth,
                rectangle.center().y() - halfHeight,
                rectangle.center().x() + halfWidth,
                rectangle.center().y() + halfHeight,
                rectangle.clockwiseDegrees());
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("개선안 JSON을 생성할 수 없습니다.", exception);
        }
    }

    /** JSON 계약을 서비스 밖으로 새 타입으로 노출하지 않기 위한 내부 직렬화 모델입니다. */
    private record ChangeData(
            int schemaVersion, String coordinateSystem, String coordinateUnit, List<ChangeOperation> operations) {}

    private record ChangeOperation(
            String operationType,
            String facilityType,
            long facilityId,
            RectangleData before,
            RectangleData after,
            String reason) {}

    private record RectangleData(double startX, double startY, double endX, double endY, double rotation) {}

    private record ChangeSummary(
            int schemaVersion,
            String summary,
            List<ChangeHighlight> changeHighlights,
            List<RiskInterpretation> riskInterpretation,
            VerificationGuide verificationGuide) {}

    private record ChangeHighlight(String title, String description) {}

    private record RiskInterpretation(String riskType, String description) {}

    private record VerificationGuide(boolean recommended, String description) {}
}

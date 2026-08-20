package com.hwalro.simulation.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.search.domain.CandidateStatus;
import com.hwalro.simulation.search.domain.ChangeOp;
import com.hwalro.simulation.search.domain.ChangeSet;
import com.hwalro.simulation.search.domain.LayoutSearchCandidateEntity;
import com.hwalro.simulation.search.domain.LayoutSearchTrialEntity;
import com.hwalro.simulation.search.domain.Metric;
import com.hwalro.simulation.search.domain.MetricDelta;
import com.hwalro.simulation.search.mapper.LayoutSearchMapper;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationSetupResponse;
import com.hwalro.simulation.simulation.engine.EngineCapacity;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.EngineResult;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.EngineRun;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.EngineRunException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CandidateTrialService {
    private static final Logger log = LoggerFactory.getLogger(CandidateTrialService.class);

    private final LayoutSearchMapper layoutStudyMapper;
    private final SimulationEngineRunner engineRunner;
    private final ChangeSetApplier changeSetApplier;
    private final EngineCapacity engineCapacity;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public CandidateTrialService(
            LayoutSearchMapper layoutStudyMapper,
            SimulationEngineRunner engineRunner,
            ChangeSetApplier changeSetApplier,
            EngineCapacity engineCapacity,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate) {
        this.layoutStudyMapper = layoutStudyMapper;
        this.engineRunner = engineRunner;
        this.changeSetApplier = changeSetApplier;
        this.engineCapacity = engineCapacity;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public TrialOutcome run(
            LayoutSearchCandidateEntity candidate,
            SimulationSetupResponse baselineSetup,
            List<Metric> baselineMetrics,
            double trialCapSeconds,
            double improvementMargin) {
        if (!startTrial(candidate.getId())) {
            return new TrialOutcome(false, null);
        }
        engineCapacity.acquire();
        try {
            ChangeSet changeSet = readChangeSet(candidate.getChangeSet());
            SimulationSetupResponse mutatedSetup = changeSetApplier.apply(baselineSetup, changeSet);
            EngineRun run = engineRunner.run(candidate.getId(), mutatedSetup, trialCapSeconds);
            List<Metric> trialMetrics = buildMetrics(run.result());
            CandidateSelector.Judgement judgement =
                    CandidateSelector.judge(trialMetrics, baselineMetrics, improvementMargin);
            recordTrialResult(candidate, run, trialMetrics, baselineMetrics, judgement);
            return new TrialOutcome(
                    true, judgement.improved() ? CandidateStatus.EVALUATED : CandidateStatus.NOT_IMPROVED);
        } catch (EngineRunException exception) {
            log.warn("Candidate {} trial engine failed (timeout={})", candidate.getId(), exception.isTimeout());
            recordTrialFailure(candidate.getId(), engineFailureMessage(exception));
            return new TrialOutcome(false, CandidateStatus.FAILED);
        } catch (RuntimeException exception) {
            log.error("Candidate {} trial failed", candidate.getId(), exception);
            recordTrialFailure(candidate.getId(), "ENGINE_ERROR: 후보 검증 실행에 실패했습니다.");
            return new TrialOutcome(false, CandidateStatus.FAILED);
        } finally {
            engineCapacity.release();
        }
    }

    private boolean startTrial(Long candidateId) {
        Boolean claimed = transactionTemplate.execute(status -> {
            if (layoutStudyMapper.claimCandidate(candidateId) != 1) {
                return false;
            }
            LocalDateTime startedAt = LocalDateTime.now();
            LayoutSearchTrialEntity existing = layoutStudyMapper.findTrialByCandidateId(candidateId);
            if (existing == null) {
                LayoutSearchCandidateEntity candidate = layoutStudyMapper.findCandidateById(candidateId);
                LayoutSearchTrialEntity trial = new LayoutSearchTrialEntity();
                trial.setCandidateId(candidateId);
                trial.setStartedAt(startedAt);
                trial.setTotalMoveDistance(totalMoveDistanceOf(candidate));
                layoutStudyMapper.insertTrial(trial);
            } else {
                if (existing.getAttemptCount() == null || existing.getAttemptCount() >= 2) {
                    throw new IllegalStateException("후보 검증 재시도 한도를 초과했습니다: candidateId=" + candidateId);
                }
                layoutStudyMapper.restartTrial(existing.getId(), startedAt);
            }
            return true;
        });
        return Boolean.TRUE.equals(claimed);
    }

    private Double candidateTotalMoveDistance(LayoutSearchCandidateEntity candidate) {
        return totalMoveDistanceOf(candidate);
    }

    private Double totalMoveDistanceOf(LayoutSearchCandidateEntity candidate) {
        if (candidate == null || candidate.getChangeSet() == null) {
            return null;
        }
        try {
            ChangeSet changeSet = objectMapper.readValue(candidate.getChangeSet(), ChangeSet.class);
            double total = 0.0;
            for (ChangeOp op : changeSet.ops()) {
                double dx = Math.abs(
                        op.after().startX().doubleValue() - op.before().startX().doubleValue());
                double dy = Math.abs(
                        op.after().startY().doubleValue() - op.before().startY().doubleValue());
                total += Math.hypot(dx, dy);
            }
            return total;
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private void recordTrialResult(
            LayoutSearchCandidateEntity candidate,
            EngineRun run,
            List<Metric> trialMetrics,
            List<Metric> baselineMetrics,
            CandidateSelector.Judgement judgement) {
        transactionTemplate.executeWithoutResult(status -> {
            EngineResult output = run.result();
            int completed = layoutStudyMapper.updateTrialResult(
                    trialIdOf(candidate.getId()),
                    output.engineVersion(),
                    output.terminationReason(),
                    writeJson(trialMetrics),
                    candidateTotalMoveDistance(candidate),
                    LocalDateTime.now(),
                    null);
            if (completed != 1) {
                return;
            }
            String candidateStatus =
                    judgement.improved() ? CandidateStatus.EVALUATED.name() : CandidateStatus.NOT_IMPROVED.name();
            if (layoutStudyMapper.updateCandidateStatus(candidate.getId(), candidateStatus, null) != 1) {
                return;
            }
            List<MetricDelta> deltas = CandidateSelector.deltas(trialMetrics, baselineMetrics);
            if (!deltas.isEmpty()) {
                layoutStudyMapper.updateCandidateMetricDelta(candidate.getId(), writeJson(deltas));
            }
        });
    }

    private void recordTrialFailure(Long candidateId, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            if (layoutStudyMapper.updateTrialResult(
                            trialIdOf(candidateId), null, null, null, null, LocalDateTime.now(), message)
                    != 1) {
                return;
            }
            layoutStudyMapper.updateCandidateStatus(candidateId, CandidateStatus.FAILED.name(), null);
        });
    }

    private Long trialIdOf(Long candidateId) {
        var trial = layoutStudyMapper.findTrialByCandidateId(candidateId);
        if (trial == null) {
            throw new IllegalStateException("후보 실행 기록이 없습니다: candidateId=" + candidateId);
        }
        return trial.getId();
    }

    private ChangeSet readChangeSet(String json) {
        try {
            return objectMapper.readValue(json, ChangeSet.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 변경 집합을 읽지 못했습니다.", exception);
        }
    }

    private List<Metric> buildMetrics(EngineResult output) {
        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric("SIMULATION_DURATION_SECONDS", "seconds", output.simulationDurationSeconds()));
        if (output.totalEvacuationTimeSeconds() != null) {
            metrics.add(new Metric("TOTAL_EVACUATION_TIME_SECONDS", "seconds", output.totalEvacuationTimeSeconds()));
        }
        if (output.averageEvacuationTimeSeconds() != null) {
            metrics.add(
                    new Metric("AVERAGE_EVACUATION_TIME_SECONDS", "seconds", output.averageEvacuationTimeSeconds()));
        }
        metrics.add(new Metric("EVACUATED_PEOPLE", "people", output.evacuatedPeople()));
        metrics.add(new Metric("REMAINING_PEOPLE", "people", output.remainingPeople()));
        metrics.add(new Metric("MAX_DENSITY", "PERSON_PER_M2", output.maxDensity()));
        return List.copyOf(metrics);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("탐색 데이터를 직렬화하지 못했습니다.", exception);
        }
    }

    private String engineFailureMessage(EngineRunException exception) {
        if (exception.isTimeout()) {
            return "ENGINE_TIMEOUT: 후보 검증 실행 시간 제한을 초과했습니다.";
        }
        return "ENGINE_ERROR: 후보 검증 엔진 실행에 실패했습니다.";
    }

    public record TrialOutcome(boolean success, CandidateStatus status) {}
}

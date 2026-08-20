package com.hwalro.simulation.search.mapper;

import com.hwalro.simulation.search.domain.LayoutSearchCandidateEntity;
import com.hwalro.simulation.search.domain.LayoutSearchEntity;
import com.hwalro.simulation.search.domain.LayoutSearchTrialEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LayoutSearchMapper {
    void insertSearch(LayoutSearchEntity search);

    Long lockBaselineSimulation(Long simulationId);

    LayoutSearchEntity findSearchById(Long id);

    LayoutSearchEntity findLatestSearchByBaselineSimulationId(Long simulationId);

    LayoutSearchEntity findActiveSearchByBaselineSimulationId(Long simulationId);

    List<LayoutSearchEntity> findActiveStudies();

    void updateStudyStarted(Long id, LocalDateTime startedAt);

    void updateStudyDiagnosis(Long id, String diagnosis);

    void updateSearchStatus(Long id, String status);

    void updatePlannerVersion(Long id, String plannerVersion);

    int finishSearch(Long id, String status, LocalDateTime finishedAt, String failureCode, String failureMessage);

    void insertCandidate(LayoutSearchCandidateEntity candidate);

    int updateCandidateStatus(Long id, String status, String rejectReason);

    int claimCandidate(Long id);

    int cancelOutstandingCandidates(Long searchId, String rejectReason);

    int cancelOutstandingTrials(Long searchId, LocalDateTime finishedAt, String failureMessage);

    void updateCandidateMetricDelta(Long id, String metricDelta);

    void markCandidatePrepared(Long id, Long layoutVersionId, Long simulationId, LocalDateTime adoptedAt);

    LayoutSearchCandidateEntity findCandidateById(Long id);

    LayoutSearchCandidateEntity findCandidateByIdForUpdate(Long id);

    List<LayoutSearchCandidateEntity> findCandidatesBySearchId(Long searchId);

    List<LayoutSearchCandidateEntity> findCandidatesByStudyIdAndRound(Long studyId, int roundIndex);

    List<LayoutSearchCandidateEntity> findCandidatesByIds(List<Long> ids);

    void insertTrial(LayoutSearchTrialEntity trial);

    void restartTrial(Long id, LocalDateTime startedAt);

    int updateTrialResult(
            Long id,
            String engineVersion,
            String terminationReason,
            String metrics,
            Double totalMoveDistance,
            LocalDateTime finishedAt,
            String failureMessage);

    LayoutSearchTrialEntity findTrialByCandidateId(Long candidateId);

    List<LayoutSearchTrialEntity> findTrialsBySearchId(Long searchId);

    List<LayoutSearchTrialEntity> findRunningTrials();

    void recoverInterruptedCandidates(String failureMessage, LocalDateTime finishedAt);
}

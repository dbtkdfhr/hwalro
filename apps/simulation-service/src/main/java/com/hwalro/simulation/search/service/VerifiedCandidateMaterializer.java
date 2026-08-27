package com.hwalro.simulation.search.service;

import com.hwalro.simulation.search.domain.LayoutSearchCandidateEntity;
import com.hwalro.simulation.search.domain.LayoutSearchEntity;
import com.hwalro.simulation.search.mapper.LayoutSearchMapper;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationSetupResponse;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.EngineRun;
import com.hwalro.simulation.simulation.service.SimulationExecutionService;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class VerifiedCandidateMaterializer {
    private static final Logger log = LoggerFactory.getLogger(VerifiedCandidateMaterializer.class);

    private final LayoutSearchMapper layoutStudyMapper;
    private final CandidateAdoptionService candidateAdoptionService;
    private final SimulationExecutionService simulationExecutionService;
    private final TransactionTemplate transactionTemplate;

    public VerifiedCandidateMaterializer(
            LayoutSearchMapper layoutStudyMapper,
            CandidateAdoptionService candidateAdoptionService,
            SimulationExecutionService simulationExecutionService,
            TransactionTemplate transactionTemplate) {
        this.layoutStudyMapper = layoutStudyMapper;
        this.candidateAdoptionService = candidateAdoptionService;
        this.simulationExecutionService = simulationExecutionService;
        this.transactionTemplate = transactionTemplate;
    }

    public void materialize(
            LayoutSearchCandidateEntity candidate, SimulationSetupResponse mutatedSetup, EngineRun run) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                LayoutSearchEntity study = layoutStudyMapper.findSearchById(candidate.getStudyId());
                if (study == null) {
                    return;
                }
                CandidateAdoptionService.AdoptedSimulation adopted =
                        candidateAdoptionService.createAdoption(study, candidate, study.getRequestedBy(), "RUNNING");
                simulationExecutionService.persistEngineRun(adopted.simulationId(), mutatedSetup, run);
                layoutStudyMapper.markCandidatePrepared(
                        candidate.getId(), adopted.layoutVersionId(), adopted.simulationId(), LocalDateTime.now());
            });
        } catch (RuntimeException exception) {
            log.warn("Layout search candidate {} materialization failed", candidate.getId(), exception);
        }
    }
}

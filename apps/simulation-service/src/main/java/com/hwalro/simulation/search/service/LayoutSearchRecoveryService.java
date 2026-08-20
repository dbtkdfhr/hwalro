package com.hwalro.simulation.search.service;

import com.hwalro.simulation.search.domain.LayoutSearchEntity;
import com.hwalro.simulation.search.mapper.LayoutSearchMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LayoutSearchRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(LayoutSearchRecoveryService.class);

    private final LayoutSearchMapper layoutSearchMapper;
    private final LayoutSearchOrchestrator orchestrator;
    private final TransactionTemplate transactionTemplate;

    public LayoutSearchRecoveryService(
            LayoutSearchMapper layoutSearchMapper,
            LayoutSearchOrchestrator orchestrator,
            TransactionTemplate transactionTemplate) {
        this.layoutSearchMapper = layoutSearchMapper;
        this.orchestrator = orchestrator;
        this.transactionTemplate = transactionTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedSearches() {
        transactionTemplate.executeWithoutResult(status -> layoutSearchMapper.recoverInterruptedCandidates(
                "SERVICE_RESTARTED: 서비스 재시작으로 검증 실행이 중단되었습니다.", LocalDateTime.now()));
        List<LayoutSearchEntity> active = layoutSearchMapper.findActiveStudies();
        for (LayoutSearchEntity search : active) {
            try {
                orchestrator.resume(search.getId());
                log.info("Resumed layout search {} after restart", search.getId());
            } catch (RuntimeException exception) {
                log.error("Could not recover layout search {}", search.getId(), exception);
            }
        }
    }
}

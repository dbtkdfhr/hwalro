package com.hwalro.simulation.zone.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EvacuationRouteWarmer {
    private static final Logger log = LoggerFactory.getLogger(EvacuationRouteWarmer.class);

    private final EvacuationPreviewService previewService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "evacuation-route-warmer");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<Long> queuedLayoutIds = ConcurrentHashMap.newKeySet();

    public EvacuationRouteWarmer(EvacuationPreviewService previewService) {
        this.previewService = previewService;
    }

    public void warm(Long layoutId) {
        if (layoutId == null || !queuedLayoutIds.add(layoutId)) {
            return;
        }
        executor.execute(() -> {
            try {
                previewService.warmLayout(layoutId);
            } catch (Exception exception) {
                log.warn("대피 경로 선계산에 실패했습니다. layoutId={}", layoutId, exception);
            } finally {
                queuedLayoutIds.remove(layoutId);
            }
        });
    }
}

package com.hwalro.regulation.common.controller;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
/** 배포 환경에서 regulation-service의 기동 상태를 확인하는 최소 상태 점검 API다. */
public class HealthController {
    @GetMapping("/api/health")
    /** 외부 API 상태와 무관하게 현재 서비스 프로세스의 기동 여부를 반환한다. */
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "regulation-service",
                "timestamp", LocalDateTime.now().toString());
    }
}

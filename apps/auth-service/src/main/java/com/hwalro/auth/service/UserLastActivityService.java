package com.hwalro.auth.service;

import com.hwalro.auth.domain.LastActivityType;
import com.hwalro.auth.domain.UserLastActivity;
import com.hwalro.auth.dto.LastActivityResponse;
import com.hwalro.auth.mapper.UserLastActivityMapper;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserLastActivityService {
    private final UserLastActivityMapper mapper;

    public UserLastActivityService(UserLastActivityMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Optional<LastActivityResponse> findLastActivity(Long userId) {
        UserLastActivity activity = mapper.findByUserId(userId);
        if (activity == null) {
            return Optional.empty();
        }
        return Optional.of(new LastActivityResponse(
                activity.getActivityType(), activity.getResourceId(), activity.getOccurredAt()));
    }

    @Transactional
    public void recordLastActivity(Long userId, String activityType, Long resourceId) {
        LastActivityType type = LastActivityType.from(activityType)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 작업 유형입니다."));
        mapper.upsert(userId, type.name(), resourceId);
    }
}

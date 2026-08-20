package com.hwalro.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hwalro.auth.domain.UserLastActivity;
import com.hwalro.auth.dto.LastActivityResponse;
import com.hwalro.auth.mapper.UserLastActivityMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserLastActivityServiceTest {
    @Mock
    private UserLastActivityMapper mapper;

    @InjectMocks
    private UserLastActivityService service;

    @Test
    void returnsRecordedLastActivity() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 11, 10, 24);
        UserLastActivity stored = new UserLastActivity();
        stored.setUserId(1L);
        stored.setActivityType("SIMULATION_SETUP");
        stored.setResourceId(42L);
        stored.setOccurredAt(occurredAt);
        when(mapper.findByUserId(1L)).thenReturn(stored);

        Optional<LastActivityResponse> response = service.findLastActivity(1L);

        assertThat(response).contains(new LastActivityResponse("SIMULATION_SETUP", 42L, occurredAt));
    }

    @Test
    void returnsEmptyWhenNoActivityRecorded() {
        when(mapper.findByUserId(1L)).thenReturn(null);

        assertThat(service.findLastActivity(1L)).isEmpty();
    }

    @Test
    void recordsLastActivityForSupportedType() {
        service.recordLastActivity(1L, "LAYOUT_EDIT", 7L);

        verify(mapper).upsert(1L, "LAYOUT_EDIT", 7L);
    }

    @Test
    void trimsActivityTypeBeforeRecording() {
        service.recordLastActivity(1L, " SIMULATION_RESULT ", 9L);

        verify(mapper).upsert(1L, "SIMULATION_RESULT", 9L);
    }

    @Test
    void rejectsUnsupportedActivityType() {
        assertThatThrownBy(() -> service.recordLastActivity(1L, "REPORT_EDIT", 3L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");

        verify(mapper, never()).upsert(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }
}

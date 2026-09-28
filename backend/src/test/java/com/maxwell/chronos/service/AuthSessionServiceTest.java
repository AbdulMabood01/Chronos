package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.AuthSession;
import com.maxwell.chronos.repository.AuthSessionRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthSessionServiceTest {
    @Test void enforcesIdleAndAbsoluteLimitsWithControllableClock() {
        AuthSessionRepository repo = mock(AuthSessionRepository.class);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        class MutableClock extends Clock {
            Instant now = start;
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now; }
        }
        MutableClock clock = new MutableClock();
        AuthSessionService service = new AuthSessionService(repo, clock);
        when(repo.save(any())).thenAnswer(call -> call.getArgument(0));
        AuthSession session = service.create(42L);
        when(repo.findById(session.id)).thenReturn(Optional.of(session));
        clock.now = start.plus(Duration.ofMinutes(29));
        assertTrue(service.valid(session.id, 42L));
        clock.now = start.plus(Duration.ofMinutes(30));
        assertFalse(service.valid(session.id, 42L));
        clock.now = start.plus(Duration.ofMinutes(29));
        assertNotNull(service.touch(session.id, 42L));
        clock.now = start.plus(Duration.ofHours(8));
        assertFalse(service.valid(session.id, 42L));
        assertNull(service.touch(session.id, 42L));
    }
}

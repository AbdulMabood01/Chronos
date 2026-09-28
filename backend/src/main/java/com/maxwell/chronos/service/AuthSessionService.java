package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.AuthSession;
import com.maxwell.chronos.repository.AuthSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthSessionService {
    public static final Duration IDLE_LIMIT = Duration.ofMinutes(30);
    public static final Duration ABSOLUTE_LIMIT = Duration.ofHours(8);
    private final AuthSessionRepository sessions;
    private final Clock clock;

    @Transactional
    public AuthSession create(Long userId) {
        Instant now = clock.instant();
        AuthSession session = new AuthSession();
        session.id = UUID.randomUUID().toString();
        session.userId = userId;
        session.createdAt = now;
        session.lastActivityAt = now;
        session.expiresAt = now.plus(ABSOLUTE_LIMIT);
        return sessions.save(session);
    }

    public boolean valid(String id, Long userId) {
        Instant now = clock.instant();
        return id != null && sessions.findById(id).filter(s -> s.userId.equals(userId)
                && s.revokedAt == null && now.isBefore(s.expiresAt)
                && now.isBefore(s.lastActivityAt.plus(IDLE_LIMIT))).isPresent();
    }

    @Transactional
    public Instant touch(String id, Long userId) {
        AuthSession s = sessions.findById(id).orElse(null);
        Instant now = clock.instant();
        if (s == null || !s.userId.equals(userId) || s.revokedAt != null
                || !now.isBefore(s.expiresAt) || !now.isBefore(s.lastActivityAt.plus(IDLE_LIMIT))) return null;
        s.lastActivityAt = now;
        return s.expiresAt;
    }

    @Transactional
    public void revoke(String id) {
        sessions.findById(id).ifPresent(s -> s.revokedAt = clock.instant());
    }

    @Transactional
    public void revokeAll(Long userId) { sessions.revokeAll(userId, clock.instant()); }
}

package com.maxwell.chronos.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {
    @Id public String id;
    @Column(nullable = false) public Long userId;
    @Column(nullable = false) public Instant createdAt;
    @Column(nullable = false) public Instant lastActivityAt;
    @Column(nullable = false) public Instant expiresAt;
    public Instant revokedAt;
}

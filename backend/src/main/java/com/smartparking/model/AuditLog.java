package com.smartparking.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Immutable audit trail for security-relevant and administrative actions. */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String actorEmail;

    private String actorRole;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(length = 120)
    private String target;

    @Column(length = 500)
    private String detail;

    @Column(length = 45)
    private String ip;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AuditLog(String actorEmail, String actorRole, String action,
                    String target, String detail, String ip) {
        this.actorEmail = actorEmail;
        this.actorRole = actorRole;
        this.action = action;
        this.target = target;
        this.detail = detail;
        this.ip = ip;
    }
}

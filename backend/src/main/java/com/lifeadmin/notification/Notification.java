package com.lifeadmin.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An in-app notification (maps to {@code notification}, V4). The {@code (reminderId, type,
 * scheduledFor)} unique index guarantees at-most-once delivery for a reminder across scheduler runs
 * and instances (G2). {@code readFlag} drives the unread badge.
 */
@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "reminder_id")
    private UUID reminderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 24)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "body", length = 1000)
    private String body;

    @Column(name = "read_flag", nullable = false)
    private boolean readFlag = false;

    @Column(name = "reference_type", length = 32)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "scheduled_for")
    private Instant scheduledFor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

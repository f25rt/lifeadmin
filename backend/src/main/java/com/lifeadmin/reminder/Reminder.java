package com.lifeadmin.reminder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.lifeadmin.common.persistence.Auditable;

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
 * A reminder to fire at {@link #scheduledForUtc} (maps to {@code reminder}, V4). Timezone handling
 * (G16): {@link #reminderLocalDate} + {@link #userTimezone} are the intent; {@link #scheduledForUtc}
 * is the precomputed instant the scheduler queries by. {@link #title} is a creation-time snapshot.
 */
@Entity
@Table(name = "reminder")
@Getter
@Setter
@NoArgsConstructor
public class Reminder extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "important_date_id")
    private UUID importantDateId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "reminder_local_date", nullable = false)
    private LocalDate reminderLocalDate;

    @Column(name = "user_timezone", nullable = false, length = 64)
    private String userTimezone;

    @Column(name = "scheduled_for_utc", nullable = false)
    private Instant scheduledForUtc;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private ReminderChannel channel = ReminderChannel.IN_APP;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReminderStatus status = ReminderStatus.SCHEDULED;

    @Column(name = "sent_at")
    private Instant sentAt;
}

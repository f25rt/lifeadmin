package com.lifeadmin.reminder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    Optional<Reminder> findByIdAndAccountId(UUID id, UUID accountId);

    List<Reminder> findByAccountIdOrderByScheduledForUtcAsc(UUID accountId);

    /** Full account deletion: purge all reminders for an account. */
    void deleteByAccountId(UUID accountId);

    // --- Analytics (cross-account funnel) ---

    /** Distinct accounts that have created at least one reminder. */
    @org.springframework.data.jpa.repository.Query(
            "select count(distinct r.accountId) from Reminder r")
    long countDistinctAccountsWithReminder();

    /** Reminders in the given status (e.g. SENT), across all accounts. */
    long countByStatus(ReminderStatus status);

    /** Due reminders the scheduler should fire: scheduled, unsent, and past their instant. */
    List<Reminder> findByStatusAndSentAtIsNullAndScheduledForUtcLessThanEqual(
            ReminderStatus status, Instant now);

    /** Important-date ids that already have a non-cancelled reminder on this account (dashboard). */
    @org.springframework.data.jpa.repository.Query("""
            select distinct r.importantDateId from Reminder r
            where r.accountId = :accountId
              and r.importantDateId is not null
              and r.status <> com.lifeadmin.reminder.ReminderStatus.CANCELLED
            """)
    List<UUID> findImportantDateIdsWithReminder(
            @org.springframework.data.repository.query.Param("accountId") UUID accountId);
}

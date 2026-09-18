package com.lifeadmin.admin;

import java.util.List;

import com.lifeadmin.account.AccountRepository;
import com.lifeadmin.account.AppUserRepository;
import com.lifeadmin.admin.api.AdminDtos.AnalyticsView;
import com.lifeadmin.admin.api.AdminDtos.FunnelStage;
import com.lifeadmin.admin.api.AdminDtos.RetentionBucket;
import com.lifeadmin.audit.AuditLogRepository;
import com.lifeadmin.document.DocumentRepository;
import com.lifeadmin.document.DocumentStatus;
import com.lifeadmin.reminder.ReminderRepository;
import com.lifeadmin.reminder.ReminderStatus;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform success-funnel analytics (spec §31), SUPER_ADMIN only. Everything is derived from data
 * we already persist (accounts, documents + status, reminders, and the audit log) rather than a
 * separate event pipeline — the funnel and rates are computed on read. Funnel stages are
 * account-level (a stage counts distinct accounts that reached it):
 *
 * <pre>
 *   signups → uploaded a document → had one processed → verified (ACTIVE) → created a reminder
 * </pre>
 *
 * plus a processing success rate (documents), a reminder conversion proxy (reminders that fired),
 * and 7/30/90-day retention (accounts active beyond their first N-day window; see the repository).
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final int[] RETENTION_DAYS = {7, 30, 90};

    private final AccountRepository accountRepository;
    private final AppUserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final ReminderRepository reminderRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public AnalyticsView analytics() {
        final long signups = userRepository.count();
        final long uploaded = documentRepository.countDistinctAccountsWithDocument();
        final long processed = documentRepository.countDistinctAccountsWithProcessedDocument();
        final long verified = documentRepository.countDistinctAccountsWithStatus(DocumentStatus.ACTIVE);
        final long withReminder = reminderRepository.countDistinctAccountsWithReminder();

        final var funnel = List.of(
                stage("Signups", signups, signups),
                stage("Uploaded a document", uploaded, signups),
                stage("Document processed", processed, signups),
                stage("Verified (activated)", verified, signups),
                stage("Created a reminder", withReminder, signups));

        // Processing success rate: ACTIVE+REVIEW_REQUIRED+ARCHIVED vs. those + FAILED (documents).
        final long ok = documentRepository.countByStatus(DocumentStatus.ACTIVE)
                + documentRepository.countByStatus(DocumentStatus.REVIEW_REQUIRED)
                + documentRepository.countByStatus(DocumentStatus.ARCHIVED);
        final long failed = documentRepository.countByStatus(DocumentStatus.FAILED);
        final double processingSuccess = percentage(ok, ok + failed);

        // Reminder conversion proxy: reminders that actually fired (SENT) vs. all non-cancelled.
        final long remindersSent = reminderRepository.countByStatus(ReminderStatus.SENT);
        final long remindersScheduled = reminderRepository.countByStatus(ReminderStatus.SCHEDULED);
        final long remindersFailed = reminderRepository.countByStatus(ReminderStatus.FAILED);
        final double reminderConversion =
                percentage(remindersSent, remindersSent + remindersScheduled + remindersFailed);

        final var retention = new java.util.ArrayList<RetentionBucket>();
        for (final int days : RETENTION_DAYS) {
            final long eligible = auditLogRepository.countAccountsOlderThanDays(days);
            final long retained = auditLogRepository.countRetainedAccounts(days);
            retention.add(new RetentionBucket(days, retained, eligible, percentage(retained, eligible)));
        }

        return new AnalyticsView(funnel, processingSuccess, ok, failed, reminderConversion, retention);
    }

    private static FunnelStage stage(final String label, final long count, final long signups) {
        return new FunnelStage(label, count, percentage(count, signups));
    }

    /** {@code numerator/denominator} as a 0–100 percentage rounded to 1 dp; 0 when denominator is 0. */
    private static double percentage(final long numerator, final long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round((numerator * 1000.0) / denominator) / 10.0;
    }
}

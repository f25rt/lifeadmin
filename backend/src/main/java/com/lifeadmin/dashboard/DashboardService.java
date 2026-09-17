package com.lifeadmin.dashboard;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.lifeadmin.account.AppUserRepository;
import com.lifeadmin.dashboard.api.DashboardDtos.AttentionItem;
import com.lifeadmin.dashboard.api.DashboardDtos.Counts;
import com.lifeadmin.dashboard.api.DashboardDtos.DashboardResponse;
import com.lifeadmin.dashboard.api.DashboardDtos.UpcomingDate;
import com.lifeadmin.document.DocumentStatus;
import com.lifeadmin.document.DocumentRepository;
import com.lifeadmin.extraction.projection.AccountDateView;
import com.lifeadmin.extraction.DateType;
import com.lifeadmin.extraction.ImportantDateRepository;
import com.lifeadmin.reminder.ReminderScheduling;
import com.lifeadmin.reminder.ReminderRepository;
import com.lifeadmin.security.auth.CurrentUser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates the home dashboard (spec §5): headline counts, upcoming important dates with a
 * timezone-aware {@code daysUntil}, and a needs-attention list. All reads are scoped to the caller's
 * account (G12). {@code daysUntil} is computed against "today" in the user's IANA zone (G16) so a
 * date is never off-by-one across the dateline.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int UPCOMING_HORIZON_DAYS = 3 * 365;
    private static final int EXPIRING_SOON_DAYS = 30;

    private final DocumentRepository documentRepository;
    private final ImportantDateRepository dateRepository;
    private final ReminderRepository reminderRepository;
    private final AppUserRepository userRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public DashboardResponse get() {
        final var principal = currentUser.require();
        final var accountId = principal.accountId();
        final var timezone = userRepository.findById(principal.userId())
                .map(u -> u.getTimezone())
                .orElse("Asia/Manila");
        final var today = LocalDate.now(ReminderScheduling.safeZone(timezone));

        final long total = documentRepository.countByAccountIdAndStatusNot(accountId, DocumentStatus.ARCHIVED);
        final long active = documentRepository.countByAccountIdAndStatus(accountId, DocumentStatus.ACTIVE);
        final long needsReview =
                documentRepository.countByAccountIdAndStatus(accountId, DocumentStatus.REVIEW_REQUIRED);

        final Set<UUID> datesWithReminder =
                new HashSet<>(reminderRepository.findImportantDateIdsWithReminder(accountId));

        final List<AccountDateView> rows = dateRepository.findUpcomingForAccount(
                accountId, today, today.plusDays(UPCOMING_HORIZON_DAYS));

        final List<UpcomingDate> upcoming = rows.stream()
                .map(r -> toUpcoming(r, today, datesWithReminder.contains(r.getId())))
                .toList();

        final long expiringSoon = upcoming.stream()
                .filter(u -> u.daysUntil() <= EXPIRING_SOON_DAYS)
                .count();

        final List<AttentionItem> attention = upcoming.stream()
                .filter(u -> !u.hasReminder())
                .filter(u -> u.daysUntil() <= EXPIRING_SOON_DAYS)
                .filter(u -> isActionableDate(u.dateType()))
                .map(u -> new AttentionItem(u.documentId(), u.documentTitle(),
                        "NO_REMINDER_CONFIGURED",
                        "No reminder set for a date " + u.daysUntil() + " day(s) away"))
                .toList();

        return new DashboardResponse(
                new Counts(total, active, needsReview, expiringSoon), upcoming, attention);
    }

    private static UpcomingDate toUpcoming(final AccountDateView r, final LocalDate today,
                                           final boolean hasReminder) {
        final long daysUntil = ChronoUnit.DAYS.between(today, r.getDateValue());
        return new UpcomingDate(
                r.getId().toString(),
                r.getDocumentId().toString(),
                r.getDocumentTitle(),
                r.getDocumentType() == null ? null : r.getDocumentType().name(),
                r.getDateType().name(),
                r.getDateValue(),
                daysUntil,
                r.getSource().name(),
                hasReminder);
    }

    private static boolean isActionableDate(final String dateType) {
        final var t = DateType.valueOf(dateType);
        return switch (t) {
            case EXPIRATION, RENEWAL, PAYMENT_DEADLINE, CONTRACT_END, WARRANTY_EXPIRATION, INSPECTION -> true;
            default -> false;
        };
    }
}

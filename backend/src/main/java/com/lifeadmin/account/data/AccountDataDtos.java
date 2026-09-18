package com.lifeadmin.account.data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DTOs for self-service data portability (Phase 5, G5): a full JSON export of the caller's account
 * data, and the confirmation payload for account deletion.
 */
public final class AccountDataDtos {

    private AccountDataDtos() {
    }

    /** The complete export document returned by {@code GET /account/export}. */
    public record AccountExport(
            Instant exportedAt,
            AccountInfo account,
            List<UserInfo> users,
            SubscriptionInfo subscription,
            List<DocumentExport> documents,
            List<ReminderExport> reminders) {
    }

    public record AccountInfo(String id, String name, Instant createdAt) {
    }

    public record UserInfo(String id, String email, String name, String role, String timezone,
                           String country, boolean disabled, Instant createdAt) {
    }

    public record SubscriptionInfo(String plan, int documentLimit, int peopleLimit,
                                   boolean aiExtractionEnabled) {
    }

    public record DocumentExport(
            String id,
            String title,
            String fileName,
            String mimeType,
            long fileSize,
            String documentType,
            String status,
            Instant createdAt,
            List<FieldExport> fields,
            List<DateExport> dates) {
    }

    public record FieldExport(String fieldName, String fieldValue, String source, boolean verified) {
    }

    public record DateExport(String dateType, LocalDate dateValue, String source) {
    }

    public record ReminderExport(String id, String documentId, String title,
                                 LocalDate reminderLocalDate, String channel, String status) {
    }

    /** Confirmation for {@code DELETE /account}. The email must match the caller's own email. */
    public record DeleteAccountRequest(String confirmEmail) {
    }
}

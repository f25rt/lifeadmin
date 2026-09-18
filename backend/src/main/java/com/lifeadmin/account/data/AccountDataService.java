package com.lifeadmin.account.data;

import java.time.Instant;

import com.lifeadmin.account.AccountRepository;
import com.lifeadmin.account.AppUser;
import com.lifeadmin.account.AppUserRepository;
import com.lifeadmin.account.SubscriptionRepository;
import com.lifeadmin.account.data.AccountDataDtos.AccountExport;
import com.lifeadmin.account.data.AccountDataDtos.AccountInfo;
import com.lifeadmin.account.data.AccountDataDtos.DateExport;
import com.lifeadmin.account.data.AccountDataDtos.DocumentExport;
import com.lifeadmin.account.data.AccountDataDtos.FieldExport;
import com.lifeadmin.account.data.AccountDataDtos.ReminderExport;
import com.lifeadmin.account.data.AccountDataDtos.SubscriptionInfo;
import com.lifeadmin.account.data.AccountDataDtos.UserInfo;
import com.lifeadmin.audit.AuditService;
import com.lifeadmin.common.error.ResourceNotFoundException;
import com.lifeadmin.common.error.ValidationException;
import com.lifeadmin.document.Document;
import com.lifeadmin.document.DocumentArtifactRepository;
import com.lifeadmin.document.DocumentRepository;
import com.lifeadmin.extraction.ExtractedFieldRepository;
import com.lifeadmin.extraction.ImportantDateRepository;
import com.lifeadmin.notification.NotificationRepository;
import com.lifeadmin.provider.storage.ObjectStorageProvider;
import com.lifeadmin.reminder.ReminderRepository;
import com.lifeadmin.security.auth.RefreshTokenRepository;
import com.lifeadmin.security.auth.CurrentUser;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service data portability (Phase 5, G5): a caller can export all of their account's data as
 * JSON, or permanently delete their account and every trace of its data. Both operate on the
 * <em>caller's own</em> account only (resolved from the security context) — there is no cross-account
 * access here. The audit log is intentionally NOT purged (it has no account FK; spec §18/G5) so the
 * record that a deletion happened survives.
 */
@Service
@RequiredArgsConstructor
public class AccountDataService {

    private static final Logger log = LoggerFactory.getLogger(AccountDataService.class);

    private final AccountRepository accountRepository;
    private final AppUserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DocumentRepository documentRepository;
    private final DocumentArtifactRepository artifactRepository;
    private final ExtractedFieldRepository fieldRepository;
    private final ImportantDateRepository dateRepository;
    private final ReminderRepository reminderRepository;
    private final NotificationRepository notificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ObjectStorageProvider storage;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    /** Assemble a full JSON-serializable export of the caller's account data. */
    @Transactional(readOnly = true)
    public AccountExport export() {
        final var principal = currentUser.require();
        final var accountId = principal.accountId();

        final var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        final var users = userRepository.findByAccountId(accountId).stream()
                .map(u -> new UserInfo(u.getId().toString(), u.getEmail(), u.getName(), u.getRole().name(),
                        u.getTimezone(), u.getCountry(), u.isDisabled(), u.getCreatedAt()))
                .toList();

        final var subscription = subscriptionRepository.findByAccountId(accountId)
                .map(s -> new SubscriptionInfo(s.getPlan().name(), s.getDocumentLimit(),
                        s.getPeopleLimit(), s.isAiExtractionEnabled()))
                .orElse(null);

        final var documents = documentRepository.findByAccountId(accountId).stream()
                .map(this::toDocumentExport)
                .toList();

        final var reminders = reminderRepository.findByAccountIdOrderByScheduledForUtcAsc(accountId).stream()
                .map(r -> new ReminderExport(r.getId().toString(), r.getDocumentId().toString(), r.getTitle(),
                        r.getReminderLocalDate(), r.getChannel().name(), r.getStatus().name()))
                .toList();

        return new AccountExport(
                Instant.now(),
                new AccountInfo(account.getId().toString(), account.getName(), account.getCreatedAt()),
                users, subscription, documents, reminders);
    }

    /**
     * Permanently delete the caller's account and all associated data. FK-safe order: object-storage
     * blobs first, then documents (DB cascades extracted_field/important_date/suggested_action/
     * reminder), then reminders/notifications/refresh-tokens not covered by cascade, then the
     * subscription, users, and finally the account row. Guarded by an email-confirmation match.
     */
    @Transactional
    public void deleteAccount(final String confirmEmail) {
        final var principal = currentUser.require();
        final var accountId = principal.accountId();

        if (confirmEmail == null || !confirmEmail.trim().equalsIgnoreCase(principal.email())) {
            throw new ValidationException("VALIDATION_ERROR",
                    "Type your account email to confirm deletion");
        }

        final var documents = documentRepository.findByAccountId(accountId);

        // 1) Purge stored blobs for every document (best-effort per object).
        for (final Document doc : documents) {
            for (final var artifact : artifactRepository.findByDocumentId(doc.getId())) {
                try {
                    storage.delete(artifact.getStorageKey());
                } catch (final Exception e) {
                    log.warn("Storage delete failed for key {} during account purge: {}",
                            artifact.getStorageKey(), e.getMessage());
                }
            }
        }

        // 2) Reminders + notifications for this account's users (notifications are user-scoped and not
        //    all reachable via document cascade, e.g. SYSTEM / NEEDS_ATTENTION rows).
        reminderRepository.deleteByAccountId(accountId);
        final var users = userRepository.findByAccountId(accountId);
        for (final AppUser u : users) {
            notificationRepository.deleteByUserId(u.getId());
            refreshTokenRepository.deleteByUserId(u.getId());
        }

        // 3) Documents (DB ON DELETE CASCADE clears extracted_field/important_date/suggested_action/
        //    document_artifact rows). Delete artifact rows explicitly too for stores without cascade.
        for (final Document doc : documents) {
            artifactRepository.deleteByDocumentId(doc.getId());
            fieldRepository.deleteByDocumentId(doc.getId());
            dateRepository.deleteByDocumentId(doc.getId());
        }
        documentRepository.deleteAll(documents);

        // 4) Subscription, users, account.
        subscriptionRepository.findByAccountId(accountId).ifPresent(subscriptionRepository::delete);
        userRepository.deleteAll(users);

        // Audit BEFORE the account row is gone (audit_log has no FK to account, so the record persists).
        auditService.record(accountId, principal.userId(), "ACCOUNT_DELETE", "Account",
                accountId.toString(), "self-service data deletion; documents=" + documents.size());

        accountRepository.deleteById(accountId);
    }

    private DocumentExport toDocumentExport(final Document d) {
        final var fields = fieldRepository.findByDocumentId(d.getId()).stream()
                .map(f -> new FieldExport(f.getFieldName(), f.getFieldValue(), f.getSource().name(),
                        f.isVerified()))
                .toList();
        final var dates = dateRepository.findByDocumentId(d.getId()).stream()
                .map(dt -> new DateExport(dt.getDateType().name(), dt.getDateValue(), dt.getSource().name()))
                .toList();
        return new DocumentExport(
                d.getId().toString(), d.getTitle(), d.getFileName(), d.getMimeType(), d.getFileSize(),
                d.getDocumentType(),
                d.getStatus().name(), d.getCreatedAt(), fields, dates);
    }
}

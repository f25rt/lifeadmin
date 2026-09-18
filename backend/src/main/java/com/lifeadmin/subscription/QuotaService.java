package com.lifeadmin.subscription;

import java.util.UUID;

import com.lifeadmin.account.SubscriptionRepository;
import com.lifeadmin.common.error.QuotaExceededException;
import com.lifeadmin.document.DocumentRepository;
import com.lifeadmin.document.DocumentStatus;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces plan limits (G3, D1). Reads the account's {@link com.lifeadmin.account.Subscription}
 * (the single source of truth for limits) and the current usage. Called by the upload flow before a
 * document is stored. Archived documents do not count toward the active-document limit.
 */
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final SubscriptionRepository subscriptionRepository;
    private final DocumentRepository documentRepository;

    @Transactional(readOnly = true)
    public void assertCanCreateDocument(final UUID accountId) {
        final var subscription = subscriptionRepository.findByAccountId(accountId)
                .orElseThrow(() -> new IllegalStateException("No subscription for account " + accountId));
        final var used = documentRepository.countByAccountIdAndStatusNot(accountId, DocumentStatus.ARCHIVED);
        if (used >= subscription.getDocumentLimit()) {
            throw new QuotaExceededException(
                    "Your " + subscription.getPlan().name() + " plan allows "
                            + subscription.getDocumentLimit() + " documents.");
        }
    }

    /** Current document usage vs. the plan limit, for surfacing in the UI (Phase 5). */
    @Transactional(readOnly = true)
    public UsageView usage(final UUID accountId) {
        final var subscription = subscriptionRepository.findByAccountId(accountId)
                .orElseThrow(() -> new IllegalStateException("No subscription for account " + accountId));
        final var used = documentRepository.countByAccountIdAndStatusNot(accountId, DocumentStatus.ARCHIVED);
        return new UsageView(subscription.getPlan().name(), used, subscription.getDocumentLimit());
    }

    /** Plan name plus documents used and allowed (archived documents are excluded from usage). */
    public record UsageView(String plan, long documentsUsed, int documentLimit) {
    }
}

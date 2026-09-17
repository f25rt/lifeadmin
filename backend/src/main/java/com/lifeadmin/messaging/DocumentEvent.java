package com.lifeadmin.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Async message envelope (spec §22). {@code eventId} drives idempotency (G11); {@code correlationId}
 * is propagated from the originating HTTP request for end-to-end tracing (G14).
 */
public record DocumentEvent(
        String eventType,
        String eventId,
        UUID documentId,
        UUID accountId,
        String correlationId,
        Instant timestamp) {

    public static final String DOCUMENT_UPLOADED = "DOCUMENT_UPLOADED";

    public static DocumentEvent uploaded(final UUID documentId, final UUID accountId, final String correlationId) {
        return new DocumentEvent(
                DOCUMENT_UPLOADED, UUID.randomUUID().toString(), documentId, accountId, correlationId, Instant.now());
    }
}

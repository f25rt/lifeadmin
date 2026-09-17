package com.lifeadmin.messaging;

import com.lifeadmin.processing.DocumentProcessingService;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Processes events inline instead of via RabbitMQ. Used when {@code lifeadmin.messaging.enabled=false}
 * (tests and no-broker local dev) so the full pipeline runs without a broker. On failure it marks
 * the document FAILED directly (there is no queue to retry/dead-letter).
 */
@Component
@ConditionalOnProperty(prefix = "lifeadmin.messaging", name = "enabled", havingValue = "false")
@RequiredArgsConstructor
public class SynchronousDocumentEventPublisher implements DocumentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SynchronousDocumentEventPublisher.class);

    private final DocumentProcessingService processingService;

    @Override
    public void publish(final DocumentEvent event) {
        try {
            processingService.process(event);
        } catch (final Exception e) {
            log.error("Synchronous processing failed for document {}: {}", event.documentId(), e.getMessage());
            processingService.markFailed(event.documentId(), e.getMessage());
        }
    }

    /** Runs inline in the caller's transaction — must not wait for a commit that may never come. */
    @Override
    public boolean deferUntilCommit() {
        return false;
    }
}

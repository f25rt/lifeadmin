package com.lifeadmin.processing;

import com.lifeadmin.common.web.CorrelationIdFilter;
import com.lifeadmin.messaging.DocumentEvent;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ worker (active when {@code lifeadmin.messaging.enabled=true}). Consumes document events
 * and runs the processing pipeline. Throwing propagates to the listener's retry; when retries are
 * exhausted the message is dead-lettered to the DLQ, where {@link #onDeadLetter} marks the document
 * FAILED (G4). The correlation id is restored into the MDC for traceable logs (G14).
 */
@Component
@ConditionalOnProperty(prefix = "lifeadmin.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DocumentProcessingWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingWorker.class);

    private final DocumentProcessingService processingService;

    @RabbitListener(queues = "#{@messagingProperties.documentProcessingQueue}")
    public void onMessage(final DocumentEvent event) {
        MDC.put(CorrelationIdFilter.MDC_KEY, event.correlationId());
        try {
            processingService.process(event);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    @RabbitListener(queues = "#{@messagingProperties.deadLetterQueue}")
    public void onDeadLetter(final DocumentEvent event) {
        log.warn("Document {} exhausted processing retries; marking FAILED", event.documentId());
        processingService.markFailed(event.documentId(),
                "Processing failed after multiple attempts. Please try re-processing.");
    }
}

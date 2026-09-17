package com.lifeadmin.processing;

import java.util.UUID;

import com.lifeadmin.account.SubscriptionRepository;
import com.lifeadmin.document.ArtifactKind;
import com.lifeadmin.document.Document;
import com.lifeadmin.document.DocumentArtifactRepository;
import com.lifeadmin.document.DocumentRepository;
import com.lifeadmin.document.DocumentStatus;
import com.lifeadmin.extraction.ExtractedField;
import com.lifeadmin.extraction.ExtractedFieldRepository;
import com.lifeadmin.extraction.ImportantDate;
import com.lifeadmin.extraction.ImportantDateRepository;
import com.lifeadmin.extraction.SuggestedAction;
import com.lifeadmin.extraction.SuggestedActionRepository;
import com.lifeadmin.messaging.DocumentEvent;
import com.lifeadmin.messaging.ProcessedEvent;
import com.lifeadmin.messaging.ProcessedEventRepository;
import com.lifeadmin.provider.ai.AiExtractionProvider;
import com.lifeadmin.provider.ocr.OcrProvider;
import com.lifeadmin.provider.storage.ObjectStorageProvider;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the OCR → AI extraction → persistence pipeline for a document (spec §21/§24). Idempotent via
 * the {@link ProcessedEvent} ledger (G11): a duplicate delivery is a no-op. On success the document
 * moves to {@code REVIEW_REQUIRED} with its extracted fields/dates/actions; failures propagate so
 * the messaging layer can retry and ultimately dead-letter (which marks the document FAILED).
 *
 * <p>AI extraction is gated on the account's {@code aiExtractionEnabled} flag; when off, the
 * document still lands in review with no auto-extracted data (the user enters details manually).
 */
@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final DocumentRepository documentRepository;
    private final DocumentArtifactRepository artifactRepository;
    private final ExtractedFieldRepository fieldRepository;
    private final ImportantDateRepository dateRepository;
    private final SuggestedActionRepository actionRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ObjectStorageProvider storage;
    private final OcrProvider ocrProvider;
    private final AiExtractionProvider aiProvider;

    /** Processes one event. Idempotent; throws on failure so the caller can retry/dead-letter. */
    @Transactional
    public void process(final DocumentEvent event) {
        if (processedEventRepository.existsByEventId(event.eventId())) {
            log.debug("Event {} already processed; skipping", event.eventId());
            return;
        }

        final var document = documentRepository.findById(event.documentId()).orElse(null);
        if (document == null) {
            log.warn("Document {} not found for event {}; skipping", event.documentId(), event.eventId());
            markProcessed(event);
            return;
        }

        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.saveAndFlush(document);

        // Re-processing (retry / explicit reprocess): clear prior derived data first.
        fieldRepository.deleteByDocumentId(document.getId());
        dateRepository.deleteByDocumentId(document.getId());
        actionRepository.deleteByDocumentId(document.getId());

        final var aiEnabled = subscriptionRepository.findByAccountId(document.getAccountId())
                .map(s -> s.isAiExtractionEnabled())
                .orElse(true);

        if (aiEnabled) {
            runExtraction(document);
        } else {
            log.info("AI extraction disabled for account {}; document {} needs manual entry",
                    document.getAccountId(), document.getId());
        }

        document.setStatus(DocumentStatus.REVIEW_REQUIRED);
        document.setFailureReason(null);
        documentRepository.save(document);
        markProcessed(event);
        log.info("Processed document {} -> REVIEW_REQUIRED (aiEnabled={})", document.getId(), aiEnabled);
    }

    /** Marks a document FAILED after the messaging layer exhausts retries (dead-letter path). */
    @Transactional
    public void markFailed(final UUID documentId, final String reason) {
        documentRepository.findById(documentId).ifPresent(document -> {
            document.setStatus(DocumentStatus.FAILED);
            document.setFailureReason(reason);
            documentRepository.save(document);
            log.warn("Document {} marked FAILED: {}", documentId, reason);
        });
    }

    private void runExtraction(final Document document) {
        final var original = artifactRepository.findByDocumentIdAndKind(document.getId(), ArtifactKind.ORIGINAL)
                .orElseThrow(() -> new IllegalStateException("No ORIGINAL artifact for document " + document.getId()));
        final var bytes = storage.get(original.getStorageKey());

        final var text = ocrProvider.extractText(bytes, document.getMimeType(), document.getFileName());
        final var result = aiProvider.classifyAndExtract(text, document.getFileName());

        document.setDocumentType(result.documentType());
        document.setClassificationConfidence(result.classificationConfidence());

        for (final var f : result.fields()) {
            final var field = new ExtractedField();
            field.setDocumentId(document.getId());
            field.setFieldName(f.fieldName());
            field.setFieldValue(f.value());
            field.setRawValue(f.rawValue());
            field.setConfidence(f.confidence());
            field.setSource(f.source());
            field.setVerified(false);
            fieldRepository.save(field);
        }
        for (final var d : result.dates()) {
            final var date = new ImportantDate();
            date.setDocumentId(document.getId());
            date.setDateType(d.dateType());
            date.setDateValue(d.dateValue());
            date.setConfidence(d.confidence());
            date.setSource(d.source());
            dateRepository.save(date);
        }
        int order = 0;
        for (final var label : result.suggestedActions()) {
            final var action = new SuggestedAction();
            action.setDocumentId(document.getId());
            action.setLabel(label);
            action.setSortOrder(order++);
            actionRepository.save(action);
        }
    }

    private void markProcessed(final DocumentEvent event) {
        final var processed = new ProcessedEvent();
        processed.setEventId(event.eventId());
        processed.setEventType(event.eventType());
        processed.setDocumentId(event.documentId());
        processedEventRepository.save(processed);
    }
}

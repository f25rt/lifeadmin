package com.lifeadmin.document;

import java.time.LocalDate;
import java.util.UUID;

import com.lifeadmin.audit.AuditService;
import com.lifeadmin.common.error.ResourceNotFoundException;
import com.lifeadmin.common.error.ValidationException;
import com.lifeadmin.common.web.CorrelationIdFilter;
import com.lifeadmin.messaging.DocumentEvent;
import com.lifeadmin.messaging.DocumentEventPublisher;
import com.lifeadmin.document.api.DocumentDtos.ActionView;
import com.lifeadmin.document.api.DocumentDtos.ArtifactView;
import com.lifeadmin.document.api.DocumentDtos.DateRequest;
import com.lifeadmin.document.api.DocumentDtos.DateView;
import com.lifeadmin.document.api.DocumentDtos.DocumentDetail;
import com.lifeadmin.document.api.DocumentDtos.DocumentSummary;
import com.lifeadmin.document.api.DocumentDtos.DownloadUrlResponse;
import com.lifeadmin.document.api.DocumentDtos.FieldView;
import com.lifeadmin.document.api.DocumentDtos.UpdateDocumentRequest;
import com.lifeadmin.document.api.DocumentDtos.UploadResponse;
import com.lifeadmin.document.api.DocumentDtos.VerifyRequest;
import com.lifeadmin.extraction.DateType;
import com.lifeadmin.extraction.ExtractedField;
import com.lifeadmin.extraction.ExtractedFieldRepository;
import com.lifeadmin.extraction.FieldSource;
import com.lifeadmin.extraction.ImportantDate;
import com.lifeadmin.extraction.ImportantDateRepository;
import com.lifeadmin.extraction.SuggestedActionRepository;
import com.lifeadmin.provider.storage.ObjectStorageProvider;
import com.lifeadmin.provider.storage.StorageProperties;
import com.lifeadmin.security.auth.CurrentUser;
import com.lifeadmin.subscription.QuotaService;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Document management (Phase 2): secure upload → object storage, ownership-scoped read/update, and
 * hard-delete that purges storage artifacts. Every operation resolves ownership through the caller's
 * account (G12): a document that exists but belongs to another account is reported as not found (to
 * avoid leaking existence). Async AI processing is wired in Phase 3; here uploads land as
 * {@code UPLOADED}.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentArtifactRepository artifactRepository;
    private final ExtractedFieldRepository fieldRepository;
    private final ImportantDateRepository dateRepository;
    private final SuggestedActionRepository actionRepository;
    private final ObjectStorageProvider storage;
    private final StorageProperties storageProperties;
    private final FileValidator fileValidator;
    private final QuotaService quotaService;
    private final AuditService auditService;
    private final DocumentEventPublisher eventPublisher;
    private final CurrentUser currentUser;

    @Transactional
    public UploadResponse upload(final byte[] content, final String originalFileName, final String personId) {
        final var principal = currentUser.require();
        final var accountId = principal.accountId();

        quotaService.assertCanCreateDocument(accountId);

        final var validated = fileValidator.validateAndNormalize(content, originalFileName);
        final var safeName = sanitizeFileName(originalFileName);

        final var document = new Document();
        document.setAccountId(accountId);
        document.setCreatedByUserId(principal.userId());
        if (personId != null && !personId.isBlank()) {
            document.setPersonId(UUID.fromString(personId));
        }
        document.setTitle(defaultTitle(safeName));
        document.setFileName(safeName);
        document.setMimeType(validated.mimeType());
        document.setFileSize(validated.storedBytes().length);
        document.setStatus(DocumentStatus.UPLOADED);
        final var saved = documentRepository.save(document);

        // Store the (normalized) original under a server-generated key; never trust client names.
        final var key = "accounts/" + accountId + "/documents/" + saved.getId() + "/original";
        storage.put(key, validated.storedBytes(), validated.mimeType());

        final var artifact = new DocumentArtifact();
        artifact.setDocumentId(saved.getId());
        artifact.setKind(ArtifactKind.ORIGINAL);
        artifact.setStorageKey(key);
        artifact.setMimeType(validated.mimeType());
        artifact.setFileSize(validated.storedBytes().length);
        artifactRepository.save(artifact);

        auditService.record(accountId, principal.userId(), "DOCUMENT_UPLOAD", "Document",
                saved.getId().toString(), "fileName=" + safeName);

        // The response reflects the accepted-for-processing state (UPLOADED). Snapshot it BEFORE
        // publishing, since the synchronous (no-broker) publisher processes inline and would
        // otherwise advance the entity's status before the response is built.
        final var response = new UploadResponse(
                saved.getId().toString(), saved.getTitle(), saved.getStatus(), safeName);

        // Enqueue async processing AFTER the transaction commits, so the worker (which reads in its
        // own transaction) always sees the persisted document + artifact.
        publishAfterCommit(DocumentEvent.uploaded(saved.getId(), accountId, MDC.get(CorrelationIdFilter.MDC_KEY)));

        return response;
    }

    /** Re-enqueue processing for a document (retry after FAILED, or re-run extraction). */
    @Transactional
    public void reprocess(final UUID id) {
        final var principal = currentUser.require();
        final var document = loadOwned(id);
        document.setStatus(DocumentStatus.UPLOADED);
        document.setFailureReason(null);
        documentRepository.save(document);
        publishAfterCommit(DocumentEvent.uploaded(document.getId(), principal.accountId(),
                MDC.get(CorrelationIdFilter.MDC_KEY)));
    }

    private void publishAfterCommit(final DocumentEvent event) {
        if (eventPublisher.deferUntilCommit() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publish(event);
                }
            });
        } else {
            eventPublisher.publish(event);
        }
    }

    @Transactional(readOnly = true)
    public Page<DocumentSummary> list(final DocumentStatus status, final Pageable pageable) {
        final var accountId = currentUser.require().accountId();
        final var page = (status == null)
                ? documentRepository.findByAccountId(accountId, pageable)
                : documentRepository.findByAccountIdAndStatus(accountId, status, pageable);
        return page.map(DocumentService::toSummary);
    }

    @Transactional(readOnly = true)
    public DocumentDetail get(final UUID id) {
        final var document = loadOwned(id);
        return toDetail(document);
    }

    /** Verify/correct extracted data → fields become source=USER/verified and status ACTIVE (spec §8). */
    @Transactional
    public DocumentDetail verify(final UUID id, final VerifyRequest request) {
        final var document = loadOwned(id);
        if (request.documentType() != null) {
            document.setDocumentType(request.documentType());
        }
        if (request.fields() != null) {
            final var existing = fieldRepository.findByDocumentId(id);
            for (final var vf : request.fields()) {
                if (vf.fieldName() == null || vf.fieldName().isBlank()) {
                    continue;
                }
                final var field = existing.stream()
                        .filter(f -> f.getFieldName().equalsIgnoreCase(vf.fieldName()))
                        .findFirst()
                        .orElseGet(() -> {
                            final var f = new ExtractedField();
                            f.setDocumentId(id);
                            f.setFieldName(vf.fieldName());
                            return f;
                        });
                field.setFieldValue(vf.fieldValue());
                field.setSource(FieldSource.USER);
                field.setVerified(true);
                fieldRepository.save(field);
            }
        }
        if (request.dates() != null) {
            for (final var vd : request.dates()) {
                if (vd.dateType() == null || vd.dateValue() == null) {
                    continue;
                }
                final var date = new ImportantDate();
                date.setDocumentId(id);
                date.setDateType(DateType.valueOf(vd.dateType()));
                date.setDateValue(LocalDate.parse(vd.dateValue()));
                date.setSource(FieldSource.USER);
                dateRepository.save(date);
            }
        }
        document.setStatus(DocumentStatus.ACTIVE);
        documentRepository.save(document);
        return toDetail(document);
    }

    // --- Important dates (manual CRUD) ---

    @Transactional
    public DateView addDate(final UUID documentId, final DateRequest request) {
        loadOwned(documentId);
        final var date = new ImportantDate();
        date.setDocumentId(documentId);
        date.setDateType(DateType.valueOf(request.dateType()));
        date.setDateValue(LocalDate.parse(request.dateValue()));
        date.setSource(FieldSource.USER);
        return toDateView(dateRepository.save(date));
    }

    @Transactional
    public DateView updateDate(final UUID documentId, final UUID dateId, final DateRequest request) {
        loadOwned(documentId);
        final var date = dateRepository.findByIdAndDocumentId(dateId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Important date", dateId));
        if (request.dateType() != null) {
            date.setDateType(DateType.valueOf(request.dateType()));
        }
        if (request.dateValue() != null) {
            date.setDateValue(LocalDate.parse(request.dateValue()));
        }
        date.setSource(FieldSource.USER);
        return toDateView(dateRepository.save(date));
    }

    @Transactional
    public void deleteDate(final UUID documentId, final UUID dateId) {
        loadOwned(documentId);
        final var date = dateRepository.findByIdAndDocumentId(dateId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Important date", dateId));
        dateRepository.delete(date);
    }

    @Transactional
    public DocumentDetail update(final UUID id, final UpdateDocumentRequest request) {
        final var document = loadOwned(id);
        if (request.title() != null && !request.title().isBlank()) {
            document.setTitle(request.title());
        }
        if (request.documentType() != null) {
            document.setDocumentType(request.documentType());
        }
        if (request.personId() != null) {
            document.setPersonId(request.personId().isBlank() ? null : UUID.fromString(request.personId()));
        }
        if (Boolean.TRUE.equals(request.archive())) {
            document.setStatus(DocumentStatus.ARCHIVED);
        }
        documentRepository.save(document);
        return get(id);
    }

    /** Hard-delete: remove storage artifacts, artifact rows, the document, and audit it (G5). */
    @Transactional
    public void delete(final UUID id) {
        final var principal = currentUser.require();
        final var document = loadOwned(id);
        final var artifacts = artifactRepository.findByDocumentId(id);
        for (final var artifact : artifacts) {
            try {
                storage.delete(artifact.getStorageKey());
            } catch (final Exception e) {
                // Continue purging DB rows even if a storage object is already gone.
            }
        }
        artifactRepository.deleteByDocumentId(id);
        documentRepository.delete(document);
        auditService.record(principal.accountId(), principal.userId(), "DOCUMENT_DELETE", "Document",
                id.toString(), null);
    }

    @Transactional(readOnly = true)
    public DownloadUrlResponse downloadUrl(final UUID id) {
        final var principal = currentUser.require();
        final var document = loadOwned(id);
        final var original = artifactRepository.findByDocumentIdAndKind(document.getId(), ArtifactKind.ORIGINAL)
                .orElseThrow(() -> new ResourceNotFoundException("Document artifact", id));
        final var url = storage.presignedGet(original.getStorageKey());
        auditService.record(principal.accountId(), principal.userId(), "DOCUMENT_DOWNLOAD", "Document",
                id.toString(), null);
        return new DownloadUrlResponse(url.toString(), storageProperties.getPresignTtlSeconds());
    }

    // --- helpers ---

    /** Loads a document scoped to the caller's account; not-found (not 403) if it isn't theirs. */
    private Document loadOwned(final UUID id) {
        final var accountId = currentUser.require().accountId();
        return documentRepository.findByIdAndAccountId(id, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
    }

    private static String sanitizeFileName(final String name) {
        if (name == null || name.isBlank()) {
            return "document";
        }
        // Keep a readable name for display/download; strip path separators and control chars.
        final var cleaned = name.replaceAll("[\\\\/\\p{Cntrl}]", "").trim();
        if (cleaned.isBlank()) {
            throw new ValidationException("VALIDATION_ERROR", "Invalid file name");
        }
        return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
    }

    /** Default display title: the file name without its extension. */
    private static String defaultTitle(final String fileName) {
        final var dot = fileName.lastIndexOf('.');
        final var base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base.isBlank() ? fileName : base;
    }

    private static DocumentSummary toSummary(final Document d) {
        return new DocumentSummary(
                d.getId().toString(), d.getTitle(), d.getFileName(), d.getDocumentType(),
                d.getStatus(), d.getFileSize(), d.getCreatedAt());
    }

    private DocumentDetail toDetail(final Document d) {
        final var artifacts = artifactRepository.findByDocumentId(d.getId()).stream()
                .map(a -> new ArtifactView(a.getKind().name(), a.getMimeType(), a.getFileSize()))
                .toList();
        final var fields = fieldRepository.findByDocumentId(d.getId()).stream()
                .map(f -> new FieldView(
                        f.getId().toString(), f.getFieldName(), f.getFieldValue(), f.getRawValue(),
                        f.getConfidence(), f.getSource().name(), f.isVerified()))
                .toList();
        final var dates = dateRepository.findByDocumentId(d.getId()).stream()
                .map(DocumentService::toDateView)
                .toList();
        final var actions = actionRepository.findByDocumentIdOrderBySortOrder(d.getId()).stream()
                .map(a -> new ActionView(a.getId().toString(), a.getLabel(), a.isDone()))
                .toList();
        return new DocumentDetail(
                d.getId().toString(), d.getTitle(), d.getFileName(), d.getMimeType(), d.getFileSize(),
                d.getDocumentType(), d.getClassificationConfidence(), d.getStatus(), d.getFailureReason(),
                d.getPersonId() == null ? null : d.getPersonId().toString(),
                artifacts, fields, dates, actions, d.getCreatedAt());
    }

    private static DateView toDateView(final ImportantDate d) {
        return new DateView(
                d.getId().toString(), d.getDateType().name(), d.getDateValue().toString(),
                d.getConfidence(), d.getSource().name(), d.getSource() == FieldSource.DERIVED);
    }
}

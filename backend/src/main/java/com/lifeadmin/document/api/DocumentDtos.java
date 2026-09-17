package com.lifeadmin.document.api;

import java.time.Instant;
import java.util.List;

import com.lifeadmin.document.DocumentStatus;
import com.lifeadmin.document.DocumentType;

import jakarta.validation.constraints.Size;

/** Request/response DTOs for document management (API_SPEC §2). */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    /** Editable metadata (PATCH). Any null field is left unchanged. */
    public record UpdateDocumentRequest(
            @Size(max = 255) String title,
            String personId,
            DocumentType documentType,
            Boolean archive) {
    }

    /** Summary shown in lists. */
    public record DocumentSummary(
            String id,
            String title,
            String fileName,
            DocumentType documentType,
            DocumentStatus status,
            long fileSize,
            Instant createdAt) {
    }

    /** Full document view including extracted data (Phase 3). */
    public record DocumentDetail(
            String id,
            String title,
            String fileName,
            String mimeType,
            long fileSize,
            DocumentType documentType,
            java.math.BigDecimal classificationConfidence,
            DocumentStatus status,
            String failureReason,
            String personId,
            List<ArtifactView> artifacts,
            List<FieldView> fields,
            List<DateView> dates,
            List<ActionView> suggestedActions,
            Instant createdAt) {
    }

    public record ArtifactView(String kind, String mimeType, long fileSize) {
    }

    public record FieldView(
            String id, String fieldName, String fieldValue, String rawValue,
            java.math.BigDecimal confidence, String source, boolean verified) {
    }

    public record DateView(
            String id, String dateType, String dateValue,
            java.math.BigDecimal confidence, String source, boolean derived) {
    }

    public record ActionView(String id, String label, boolean done) {
    }

    /** Verify/correct extracted data (spec §8). Any provided item becomes source=USER, verified. */
    public record VerifyRequest(
            DocumentType documentType,
            List<VerifyField> fields,
            List<VerifyDate> dates) {
    }

    public record VerifyField(@Size(max = 64) String fieldName, @Size(max = 2000) String fieldValue) {
    }

    public record VerifyDate(String dateType, String dateValue) {
    }

    /** Add/update an important date manually. */
    public record DateRequest(String dateType, String dateValue) {
    }

    /** Response to a successful upload (202). */
    public record UploadResponse(String id, String title, DocumentStatus status, String fileName) {
    }

    /** Response for a download request — a short-lived signed URL (G10). */
    public record DownloadUrlResponse(String url, long expiresInSeconds) {
    }
}

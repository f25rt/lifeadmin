package com.lifeadmin.admin.api;

import java.time.Instant;
import java.util.List;

/** Response DTOs for the platform admin API (SUPER_ADMIN only). */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** Platform-wide headline stats for the admin overview. */
    public record OverviewStats(
            long totalUsers,
            long totalAccounts,
            long totalDocuments,
            long documentsActive,
            long documentsProcessing,
            long documentsFailed) {
    }

    /** A user row for the admin users list, with their document count. */
    public record AdminUserRow(
            String id,
            String email,
            String name,
            String role,
            String accountId,
            String timezone,
            String country,
            long documentCount,
            Instant createdAt) {
    }

    /** A document row for the admin documents browser. */
    public record AdminDocumentRow(
            String id,
            String title,
            String fileName,
            String documentType,
            String status,
            long fileSize,
            String accountId,
            String uploadedByUserId,
            Instant createdAt) {
    }

    public record AdminUserList(List<AdminUserRow> users) {
    }

    // --- Upload rules ---

    public record UploadRulesView(
            List<String> allowedMimeTypes,
            List<String> supportedMimeTypes,
            long maxFileSizeBytes,
            int maxPdfPages) {
    }

    public record UpdateUploadRulesRequest(
            List<String> allowedMimeTypes,
            Long maxFileSizeBytes,
            Integer maxPdfPages) {
    }

    // --- Document types + AI templates ---

    public record DocumentTypeView(
            String typeCode,
            String label,
            boolean enabled,
            String keywords,
            String relevantDateTypes,
            String defaultOffsetsDays,
            int sortOrder) {
    }

    public record DocumentTypeList(List<DocumentTypeView> types) {
    }

    /** Partial update of a document type / AI template; null fields are left unchanged. */
    public record UpdateDocumentTypeRequest(
            String label,
            Boolean enabled,
            String keywords,
            String relevantDateTypes,
            String defaultOffsetsDays) {
    }
}

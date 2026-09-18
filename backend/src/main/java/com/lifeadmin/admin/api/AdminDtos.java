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
            boolean disabled,
            long documentCount,
            Instant createdAt) {
    }

    /** Change a user's role (SUPER_ADMIN/OWNER/ADMIN/MEMBER/VIEWER). */
    public record UpdateUserRoleRequest(String role) {
    }

    /** Enable or disable (soft-lock login for) a user. */
    public record UpdateUserStatusRequest(Boolean disabled) {
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

    // --- Analytics / success funnel (spec §31) ---

    /**
     * Platform success funnel plus derived rates. The funnel stages are account-level counts
     * (visitors are not tracked server-side, so it starts at signups); rates are 0–100 percentages.
     */
    public record AnalyticsView(
            List<FunnelStage> funnel,
            double processingSuccessRate,
            long documentsProcessedOk,
            long documentsFailed,
            double reminderConversionRate,
            List<RetentionBucket> retention) {
    }

    /** One stage of the funnel: a label, the count, and its % of the first (signups) stage. */
    public record FunnelStage(String stage, long count, double pctOfSignups) {
    }

    /** Retention at a horizon (e.g. 7 days): retained accounts vs. those old enough to qualify. */
    public record RetentionBucket(int days, long retained, long eligible, double rate) {
    }

    /** Partial update of a document type / AI template; null fields are left unchanged. */
    public record UpdateDocumentTypeRequest(
            String label,
            Boolean enabled,
            String keywords,
            String relevantDateTypes,
            String defaultOffsetsDays) {
    }

    /** Create a new admin-defined document type. {@code typeCode} is normalized to UPPER_SNAKE. */
    public record CreateDocumentTypeRequest(
            String typeCode,
            String label,
            String keywords,
            String relevantDateTypes,
            String defaultOffsetsDays,
            Integer sortOrder) {
    }
}

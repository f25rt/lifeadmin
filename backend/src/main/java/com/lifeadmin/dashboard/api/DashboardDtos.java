package com.lifeadmin.dashboard.api;

import java.time.LocalDate;
import java.util.List;

/** Response DTOs for the home dashboard (spec §5). */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record DashboardResponse(
            Counts counts,
            List<UpcomingDate> upcoming,
            List<AttentionItem> needsAttention) {
    }

    /** Headline tiles. */
    public record Counts(
            long totalDocuments,
            long activeDocuments,
            long needsReview,
            long expiringSoon) {
    }

    /** An upcoming important date with a timezone-aware day count. */
    public record UpcomingDate(
            String importantDateId,
            String documentId,
            String documentTitle,
            String documentType,
            String dateType,
            LocalDate dateValue,
            long daysUntil,
            String source,
            boolean hasReminder) {
    }

    /** Something the user should act on, with a machine-readable reason. */
    public record AttentionItem(
            String documentId,
            String documentTitle,
            String reason,
            String detail) {
    }
}

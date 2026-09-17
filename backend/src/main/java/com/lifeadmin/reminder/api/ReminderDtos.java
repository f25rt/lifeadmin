package com.lifeadmin.reminder.api;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotNull;

/** Request/response DTOs for reminders (API_SPEC §4). */
public final class ReminderDtos {

    private ReminderDtos() {
    }

    /**
     * Create reminders for a document. Either provide {@code importantDateId} + {@code offsetsDaysBefore}
     * (one reminder per offset, relative to that date) or an explicit {@code reminderDate}.
     */
    public record CreateReminderRequest(
            @NotNull String documentId,
            String importantDateId,
            List<Integer> offsetsDaysBefore,
            String reminderDate,
            String channel,
            /** "BEFORE" (default) or "AFTER": whether each offset is before or after the date. */
            String direction) {
    }

    public record ReminderResponse(
            String id,
            String documentId,
            String title,
            String reminderLocalDate,
            Instant scheduledForUtc,
            String channel,
            String status,
            Instant sentAt) {
    }

    public record CreateReminderResponse(List<ReminderResponse> created) {
    }

    /** Reschedule / change channel / cancel. */
    public record UpdateReminderRequest(String reminderDate, String channel, Boolean cancel) {
    }
}

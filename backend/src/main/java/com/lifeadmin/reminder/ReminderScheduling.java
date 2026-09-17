package com.lifeadmin.reminder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Pure timezone math for reminders (G16), extracted so it is unit-testable without Spring. A reminder
 * fires at {@code sendAtHour} local time on {@code localDate} in the user's IANA zone; this returns
 * the corresponding UTC instant the scheduler compares against {@code now()}.
 */
public final class ReminderScheduling {

    private ReminderScheduling() {
    }

    public static Instant toScheduledInstant(final LocalDate localDate, final String ianaZone, final int sendAtHour) {
        final var zone = safeZone(ianaZone);
        return localDate.atTime(sendAtHour, 0).atZone(zone).toInstant();
    }

    public static ZoneId safeZone(final String ianaZone) {
        try {
            return ianaZone == null || ianaZone.isBlank() ? ZoneId.of("Asia/Manila") : ZoneId.of(ianaZone);
        } catch (final Exception e) {
            return ZoneId.of("Asia/Manila");
        }
    }
}

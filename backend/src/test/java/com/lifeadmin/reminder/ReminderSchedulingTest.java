package com.lifeadmin.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

/** Pure timezone math for reminder scheduling (G16) — no Spring context needed. */
class ReminderSchedulingTest {

    @Test
    void firesAtLocalSendHourInUsersZone() {
        final var instant = ReminderScheduling.toScheduledInstant(
                LocalDate.of(2026, 3, 15), "Asia/Manila", 9);

        // 09:00 in Manila (UTC+8) is 01:00 UTC.
        final var expected = ZonedDateTime.of(2026, 3, 15, 9, 0, 0, 0, ZoneId.of("Asia/Manila")).toInstant();
        assertThat(instant).isEqualTo(expected);
        assertThat(instant).isEqualTo(Instant.parse("2026-03-15T01:00:00Z"));
    }

    @Test
    void differentZonesProduceDifferentInstants() {
        final var manila = ReminderScheduling.toScheduledInstant(LocalDate.of(2026, 3, 15), "Asia/Manila", 9);
        final var newYork = ReminderScheduling.toScheduledInstant(LocalDate.of(2026, 3, 15), "America/New_York", 9);
        assertThat(manila).isNotEqualTo(newYork);
        // New York 09:00 is later in UTC than Manila 09:00.
        assertThat(newYork).isAfter(manila);
    }

    @Test
    void blankOrInvalidZoneFallsBackToManila() {
        assertThat(ReminderScheduling.safeZone(null)).isEqualTo(ZoneId.of("Asia/Manila"));
        assertThat(ReminderScheduling.safeZone("")).isEqualTo(ZoneId.of("Asia/Manila"));
        assertThat(ReminderScheduling.safeZone("Not/AZone")).isEqualTo(ZoneId.of("Asia/Manila"));
        assertThat(ReminderScheduling.safeZone("Europe/Berlin")).isEqualTo(ZoneId.of("Europe/Berlin"));
    }
}

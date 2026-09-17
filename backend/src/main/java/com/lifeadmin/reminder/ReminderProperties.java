package com.lifeadmin.reminder;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code lifeadmin.reminder.*} — scheduler cadence and the local hour reminders fire. */
@ConfigurationProperties(prefix = "lifeadmin.reminder")
public class ReminderProperties {

    private long pollIntervalMs = 60_000;
    private int sendAtHour = 9;

    public long getPollIntervalMs() { return pollIntervalMs; }
    public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }

    public int getSendAtHour() { return sendAtHour; }
    public void setSendAtHour(int sendAtHour) { this.sendAtHour = sendAtHour; }
}

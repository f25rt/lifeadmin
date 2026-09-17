package com.lifeadmin.reminder;

/** Reminder lifecycle. SCHEDULED → SENT (fired), or CANCELLED/FAILED. */
public enum ReminderStatus {
    SCHEDULED,
    SENT,
    CANCELLED,
    FAILED
}

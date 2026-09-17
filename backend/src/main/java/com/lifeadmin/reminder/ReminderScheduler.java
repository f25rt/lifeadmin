package com.lifeadmin.reminder;

import java.time.Instant;
import java.util.List;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls for due reminders and fires them (spec §11). Runs at most once across instances via ShedLock
 * ({@link SchedulerLock}, G2); each due reminder is fired in its own transaction by
 * {@link ReminderFiringService} so one failure doesn't abort the batch. Exposed {@link #poll()} for
 * direct invocation from integration tests.
 */
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderRepository reminderRepository;
    private final ReminderFiringService firingService;

    @Scheduled(fixedDelayString = "${lifeadmin.reminder.poll-interval-ms:60000}")
    @SchedulerLock(name = "reminderScheduler", lockAtLeastFor = "PT5S", lockAtMostFor = "PT5M")
    public void run() {
        poll();
    }

    /** Fire all due reminders once. Returns the number of reminders fired. */
    public int poll() {
        final List<Reminder> due = reminderRepository
                .findByStatusAndSentAtIsNullAndScheduledForUtcLessThanEqual(ReminderStatus.SCHEDULED, Instant.now());
        if (due.isEmpty()) {
            return 0;
        }
        int fired = 0;
        for (final var reminder : due) {
            try {
                firingService.fire(reminder);
                fired++;
            } catch (final RuntimeException e) {
                log.error("Failed to fire reminder {}: {}", reminder.getId(), e.getMessage(), e);
            }
        }
        log.info("Reminder scheduler fired {} of {} due reminder(s)", fired, due.size());
        return fired;
    }
}

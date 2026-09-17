package com.lifeadmin.reminder;

import java.time.Instant;

import com.lifeadmin.account.AppUserRepository;
import com.lifeadmin.notification.NotificationService;
import com.lifeadmin.provider.email.EmailProvider;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fires a single due reminder in its own transaction (spec §11/§16). For each user on the reminder's
 * account it creates an in-app notification idempotently ({@code (reminderId, type)} guard, G2); if a
 * new notification was created and the channel is EMAIL, it also sends an email. The reminder is then
 * stamped SENT. Isolating one reminder per transaction means a single failure cannot roll back the
 * whole scheduler batch.
 */
@Service
@RequiredArgsConstructor
public class ReminderFiringService {

    private static final Logger log = LoggerFactory.getLogger(ReminderFiringService.class);

    private final ReminderRepository reminderRepository;
    private final AppUserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailProvider emailProvider;

    @Transactional
    public void fire(final Reminder reminder) {
        final var users = userRepository.findByAccountId(reminder.getAccountId());
        for (final var user : users) {
            final boolean created = notificationService.createForReminder(
                    user.getId(), reminder.getId(), reminder.getTitle(),
                    bodyFor(reminder), reminder.getScheduledForUtc());
            if (created && reminder.getChannel() == ReminderChannel.EMAIL) {
                try {
                    emailProvider.send(user.getEmail(), reminder.getTitle(), bodyFor(reminder));
                } catch (final RuntimeException e) {
                    // Email is best-effort; the in-app notification already landed. Don't fail the tx.
                    log.warn("Failed to send reminder email to {} for reminder {}: {}",
                            user.getEmail(), reminder.getId(), e.getMessage());
                }
            }
        }
        reminder.setStatus(ReminderStatus.SENT);
        reminder.setSentAt(Instant.now());
        reminderRepository.save(reminder);
    }

    private static String bodyFor(final Reminder reminder) {
        return reminder.getTitle() + " (due " + reminder.getReminderLocalDate() + ").";
    }
}

package com.lifeadmin.notification;

import java.util.List;
import java.util.UUID;

import com.lifeadmin.common.error.ResourceNotFoundException;
import com.lifeadmin.notification.api.NotificationDtos.NotificationResponse;
import com.lifeadmin.notification.api.NotificationDtos.UnreadCountResponse;
import com.lifeadmin.security.auth.CurrentUser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and reads in-app notifications (spec §16). Delivery for reminders goes through
 * {@link #createForReminder} which is idempotent: the {@code (reminderId, type)} guard prevents a
 * double-send if the scheduler runs twice (G2). Read/list operations are scoped to the caller.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CurrentUser currentUser;

    /**
     * Idempotently create a reminder notification for a user. Returns {@code true} if a new
     * notification was created (i.e. the caller should also send email), {@code false} if one
     * already existed for this reminder.
     */
    @Transactional
    public boolean createForReminder(final UUID userId, final UUID reminderId, final String title,
                                     final String body, final java.time.Instant scheduledFor) {
        if (notificationRepository.existsByUserIdAndReminderIdAndType(userId, reminderId, NotificationType.REMINDER)) {
            return false;
        }
        final var notification = new Notification();
        notification.setUserId(userId);
        notification.setReminderId(reminderId);
        notification.setType(NotificationType.REMINDER);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setReferenceType("REMINDER");
        notification.setReferenceId(reminderId);
        notification.setScheduledFor(scheduledFor);
        notificationRepository.save(notification);
        return true;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(final boolean unreadOnly) {
        final var userId = currentUser.require().userId();
        final var notifications = unreadOnly
                ? notificationRepository.findByUserIdAndReadFlagFalseOrderByCreatedAtDesc(userId)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return notifications.stream().map(NotificationService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        final var userId = currentUser.require().userId();
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadFlagFalse(userId));
    }

    @Transactional
    public NotificationResponse markRead(final UUID id) {
        final var userId = currentUser.require().userId();
        final var notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        notification.setReadFlag(true);
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public int markAllRead() {
        final var userId = currentUser.require().userId();
        return notificationRepository.markAllRead(userId);
    }

    private static NotificationResponse toResponse(final Notification n) {
        return new NotificationResponse(
                n.getId().toString(),
                n.getType().name(),
                n.getTitle(),
                n.getBody(),
                n.isReadFlag(),
                n.getReferenceType(),
                n.getReferenceId() == null ? null : n.getReferenceId().toString(),
                n.getCreatedAt());
    }
}

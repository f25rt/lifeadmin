package com.lifeadmin.notification.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.lifeadmin.notification.NotificationService;
import com.lifeadmin.notification.api.NotificationDtos.NotificationResponse;
import com.lifeadmin.notification.api.NotificationDtos.UnreadCountResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notification API (API_SPEC §5). All endpoints require authentication and are scoped to the caller.
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> list(
            @RequestParam(value = "unread", defaultValue = "false") final boolean unread) {
        return notificationService.list(unread);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount() {
        return notificationService.unreadCount();
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable final UUID id) {
        return notificationService.markRead(id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", notificationService.markAllRead());
    }
}

package com.lifeadmin.reminder.api;

import java.util.List;
import java.util.UUID;

import com.lifeadmin.reminder.ReminderService;
import com.lifeadmin.reminder.api.ReminderDtos.CreateReminderRequest;
import com.lifeadmin.reminder.api.ReminderDtos.CreateReminderResponse;
import com.lifeadmin.reminder.api.ReminderDtos.ReminderResponse;
import com.lifeadmin.reminder.api.ReminderDtos.UpdateReminderRequest;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reminder API (API_SPEC §4). All endpoints require authentication and are scoped to the caller's
 * account. Reminders derive from an important date (one per "N days before" offset) or an explicit
 * date; each is stamped with a timezone-aware {@code scheduledForUtc}.
 */
@RestController
@RequestMapping("/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @PostMapping
    public ResponseEntity<CreateReminderResponse> create(
            @Valid @RequestBody final CreateReminderRequest request) {
        final List<ReminderResponse> created = reminderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateReminderResponse(created));
    }

    @GetMapping
    public List<ReminderResponse> list(
            @RequestParam(value = "upcoming", defaultValue = "false") final boolean upcoming) {
        return reminderService.list(upcoming);
    }

    @PatchMapping("/{id}")
    public ReminderResponse update(
            @PathVariable final UUID id, @Valid @RequestBody final UpdateReminderRequest request) {
        return reminderService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable final UUID id) {
        reminderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

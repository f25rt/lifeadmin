package com.lifeadmin.reminder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.lifeadmin.account.AppUserRepository;
import com.lifeadmin.common.error.ResourceNotFoundException;
import com.lifeadmin.common.error.ValidationException;
import com.lifeadmin.document.Document;
import com.lifeadmin.document.DocumentRepository;
import com.lifeadmin.extraction.ImportantDate;
import com.lifeadmin.extraction.ImportantDateRepository;
import com.lifeadmin.reminder.api.ReminderDtos.CreateReminderRequest;
import com.lifeadmin.reminder.api.ReminderDtos.ReminderResponse;
import com.lifeadmin.reminder.api.ReminderDtos.UpdateReminderRequest;
import com.lifeadmin.security.auth.CurrentUser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and manages reminders (spec §11). Reminders derive from an important date (one per
 * "N days before" offset) or an explicit date, and are stamped with a timezone-aware
 * {@code scheduledForUtc} (G16). All operations are scoped to the caller's account.
 */
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final DocumentRepository documentRepository;
    private final ImportantDateRepository dateRepository;
    private final AppUserRepository userRepository;
    private final CurrentUser currentUser;
    private final ReminderProperties properties;

    @Transactional
    public List<ReminderResponse> create(final CreateReminderRequest request) {
        final var principal = currentUser.require();
        final var accountId = principal.accountId();
        final var document = documentRepository.findByIdAndAccountId(UUID.fromString(request.documentId()), accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", request.documentId()));

        final var timezone = userRepository.findById(principal.userId())
                .map(u -> u.getTimezone())
                .orElse("Asia/Manila");
        final var channel = parseChannel(request.channel());

        final var created = new ArrayList<Reminder>();

        if (request.importantDateId() != null && !request.importantDateId().isBlank()) {
            final var importantDate = dateRepository.findByIdAndDocumentId(
                            UUID.fromString(request.importantDateId()), document.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Important date", request.importantDateId()));
            final var after = isAfter(request.direction());
            final var offsets = (request.offsetsDaysBefore() == null || request.offsetsDaysBefore().isEmpty())
                    ? List.of(7)
                    : request.offsetsDaysBefore();
            for (final var offset : offsets) {
                final var localDate = after
                        ? importantDate.getDateValue().plusDays(offset)
                        : importantDate.getDateValue().minusDays(offset);
                created.add(persist(document, importantDate, localDate, timezone, channel,
                        titleFor(document, importantDate, offset, after)));
            }
        } else if (request.reminderDate() != null && !request.reminderDate().isBlank()) {
            final var localDate = LocalDate.parse(request.reminderDate());
            created.add(persist(document, null, localDate, timezone, channel,
                    "Reminder: " + document.getTitle()));
        } else {
            throw new ValidationException("VALIDATION_ERROR",
                    "Provide either importantDateId (+offsets) or an explicit reminderDate");
        }

        return created.stream().map(ReminderService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ReminderResponse> list(final boolean upcomingOnly) {
        final var accountId = currentUser.require().accountId();
        return reminderRepository.findByAccountIdOrderByScheduledForUtcAsc(accountId).stream()
                .filter(r -> !upcomingOnly || r.getStatus() == ReminderStatus.SCHEDULED)
                .map(ReminderService::toResponse)
                .toList();
    }

    @Transactional
    public ReminderResponse update(final UUID id, final UpdateReminderRequest request) {
        final var principal = currentUser.require();
        final var reminder = reminderRepository.findByIdAndAccountId(id, principal.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Reminder", id));
        if (Boolean.TRUE.equals(request.cancel())) {
            reminder.setStatus(ReminderStatus.CANCELLED);
        }
        if (request.channel() != null) {
            reminder.setChannel(parseChannel(request.channel()));
        }
        if (request.reminderDate() != null && !request.reminderDate().isBlank()) {
            final var localDate = LocalDate.parse(request.reminderDate());
            reminder.setReminderLocalDate(localDate);
            reminder.setScheduledForUtc(ReminderScheduling.toScheduledInstant(
                    localDate, reminder.getUserTimezone(), properties.getSendAtHour()));
        }
        return toResponse(reminderRepository.save(reminder));
    }

    @Transactional
    public void delete(final UUID id) {
        final var principal = currentUser.require();
        final var reminder = reminderRepository.findByIdAndAccountId(id, principal.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Reminder", id));
        reminderRepository.delete(reminder);
    }

    // --- helpers ---

    private Reminder persist(final Document document, final ImportantDate importantDate, final LocalDate localDate,
                             final String timezone, final ReminderChannel channel, final String title) {
        final var reminder = new Reminder();
        reminder.setAccountId(document.getAccountId());
        reminder.setDocumentId(document.getId());
        if (importantDate != null) {
            reminder.setImportantDateId(importantDate.getId());
        }
        reminder.setTitle(title);
        reminder.setReminderLocalDate(localDate);
        reminder.setUserTimezone(timezone);
        reminder.setScheduledForUtc(
                ReminderScheduling.toScheduledInstant(localDate, timezone, properties.getSendAtHour()));
        reminder.setChannel(channel);
        reminder.setStatus(ReminderStatus.SCHEDULED);
        return reminderRepository.save(reminder);
    }

    private static String titleFor(final Document document, final ImportantDate date, final int offset,
                                   final boolean after) {
        final var what = document.getTitle();
        final var kind = date.getDateType().name().toLowerCase().replace('_', ' ');
        final var days = offset + " day" + (offset == 1 ? "" : "s");
        if (offset == 0) {
            return what + " — " + kind + " today";
        }
        return after
                ? what + " — " + days + " after " + kind
                : what + " — " + kind + " in " + days;
    }

    private static boolean isAfter(final String direction) {
        return direction != null && "AFTER".equalsIgnoreCase(direction.trim());
    }

    private static ReminderChannel parseChannel(final String channel) {
        if (channel == null || channel.isBlank()) {
            return ReminderChannel.IN_APP;
        }
        try {
            return ReminderChannel.valueOf(channel.toUpperCase());
        } catch (final IllegalArgumentException e) {
            throw new ValidationException("VALIDATION_ERROR", "Invalid channel: " + channel);
        }
    }

    private static ReminderResponse toResponse(final Reminder r) {
        return new ReminderResponse(
                r.getId().toString(), r.getDocumentId().toString(), r.getTitle(),
                r.getReminderLocalDate().toString(), r.getScheduledForUtc(),
                r.getChannel().name(), r.getStatus().name(), r.getSentAt());
    }
}

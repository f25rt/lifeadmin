package com.lifeadmin.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeadmin.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end reminder → scheduler → notification flow. Uploads a warranty (which yields an important
 * date), creates a reminder via the API, backdates it so the scheduler treats it as due, invokes the
 * scheduler directly, and asserts an in-app notification was created and the reminder marked SENT.
 * A second poll must be idempotent (no duplicate notification).
 *
 * <p>Not {@code @Transactional}: the scheduler fires reminders in their own transactions, so the
 * test verifies committed state. Each test uses a unique email to isolate its account.
 */
@AutoConfigureMockMvc
class ReminderFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ReminderRepository reminderRepository;
    @Autowired
    private ReminderScheduler scheduler;

    private String registerAndLogin(final String email) throws Exception {
        mockMvc.perform(post(BASE + "/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Juan","email":"%s","password":"ChangeMe123!"}""".formatted(email)))
                .andExpect(status().isCreated());
        final var res = mockMvc.perform(post(BASE + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"ChangeMe123!"}""".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("accessToken").asText();
    }

    private static byte[] png() throws Exception {
        final var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private String uploadWarrantyAndGetDateId(final String token) throws Exception {
        final var file = new MockMultipartFile("file", "samsung_warranty_receipt.png", "image/png", png());
        final var id = objectMapper.readTree(
                mockMvc.perform(multipart(BASE + "/documents").file(file).header("Authorization", "Bearer " + token))
                        .andExpect(status().isAccepted())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asText();

        final var detail = objectMapper.readTree(
                mockMvc.perform(get(BASE + "/documents/" + id).header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        // Return the first important date id.
        return detail.get("dates").get(0).get("id").asText();
    }

    @Test
    void dueReminderProducesNotificationAndIsIdempotent() throws Exception {
        final var token = registerAndLogin("reminder@example.com");
        final var dateId = uploadWarrantyAndGetDateId(token);

        // Create a reminder 7 days before the important date.
        final var createRes = mockMvc.perform(post(BASE + "/reminders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentId":"%s","importantDateId":"%s","offsetsDaysBefore":[7],"channel":"IN_APP"}"""
                                .formatted(documentIdFor(token), dateId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created.length()", is(1)))
                .andReturn().getResponse().getContentAsString();
        final var reminderId = UUID.fromString(
                objectMapper.readTree(createRes).get("created").get(0).get("id").asText());

        // Backdate it so the scheduler treats it as due right now.
        final var reminder = reminderRepository.findById(reminderId).orElseThrow();
        reminder.setScheduledForUtc(Instant.now().minus(1, ChronoUnit.HOURS));
        reminderRepository.save(reminder);

        // First poll fires it: one notification created, reminder SENT.
        assertThat(scheduler.poll()).isEqualTo(1);

        mockMvc.perform(get(BASE + "/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].type", is("REMINDER")))
                .andExpect(jsonPath("$[0].read", is(false)));

        mockMvc.perform(get(BASE + "/notifications/unread-count").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread", is(1)));

        assertThat(reminderRepository.findById(reminderId).orElseThrow().getStatus())
                .isEqualTo(ReminderStatus.SENT);

        // Second poll is idempotent: nothing due, no duplicate notification.
        assertThat(scheduler.poll()).isEqualTo(0);
        mockMvc.perform(get(BASE + "/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)));

        // Mark all read → unread count zero.
        mockMvc.perform(post(BASE + "/notifications/read-all").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated", greaterThanOrEqualTo(1)));
        mockMvc.perform(get(BASE + "/notifications/unread-count").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread", is(0)));

        // Clean up committed rows so the shared container stays isolated between tests.
        reminderRepository.deleteById(reminderId);
    }

    @Test
    void dashboardListsUpcomingDatesWithDayCounts() throws Exception {
        final var token = registerAndLogin("dashboard@example.com");
        uploadWarrantyAndGetDateId(token);

        mockMvc.perform(get(BASE + "/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.totalDocuments", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.upcoming.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.upcoming[0].daysUntil", greaterThanOrEqualTo(0)));
    }

    @Test
    void afterDirectionSchedulesReminderPastTheDate() throws Exception {
        final var token = registerAndLogin("after@example.com");
        final var documentId = documentIdForUpload(token);
        final var detail = objectMapper.readTree(
                mockMvc.perform(get(BASE + "/documents/" + documentId).header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        final var dateNode = detail.get("dates").get(0);
        final var dateId = dateNode.get("id").asText();
        final var dateValue = java.time.LocalDate.parse(dateNode.get("dateValue").asText());

        // "1 week AFTER" the date → reminderLocalDate == date + 7.
        final var res = mockMvc.perform(post(BASE + "/reminders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentId":"%s","importantDateId":"%s","offsetsDaysBefore":[7],
                                 "direction":"AFTER","channel":"IN_APP"}"""
                                .formatted(documentId, dateId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created.length()", is(1)))
                .andExpect(jsonPath("$.created[0].reminderLocalDate", is(dateValue.plusDays(7).toString())))
                .andReturn().getResponse().getContentAsString();

        // And a BEFORE reminder on the same date lands 7 days earlier, proving the direction matters.
        final var beforeRes = mockMvc.perform(post(BASE + "/reminders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentId":"%s","importantDateId":"%s","offsetsDaysBefore":[7],
                                 "direction":"BEFORE","channel":"IN_APP"}"""
                                .formatted(documentId, dateId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created[0].reminderLocalDate", is(dateValue.minusDays(7).toString())))
                .andReturn().getResponse().getContentAsString();

        // Clean up committed rows so the shared container stays isolated between tests.
        reminderRepository.deleteById(
                UUID.fromString(objectMapper.readTree(res).get("created").get(0).get("id").asText()));
        reminderRepository.deleteById(
                UUID.fromString(objectMapper.readTree(beforeRes).get("created").get(0).get("id").asText()));
    }

    /** Upload a warranty and return the document id (no date lookup). */
    private String documentIdForUpload(final String token) throws Exception {
        uploadWarrantyAndGetDateId(token);
        return documentIdFor(token);
    }

    /** Look up the caller's single document id (the warranty just uploaded). */
    private String documentIdFor(final String token) throws Exception {
        final var list = objectMapper.readTree(
                mockMvc.perform(get(BASE + "/documents").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        return list.get("content").get(0).get("id").asText();
    }
}

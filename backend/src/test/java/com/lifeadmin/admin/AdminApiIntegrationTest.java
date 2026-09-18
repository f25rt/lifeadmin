package com.lifeadmin.admin;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeadmin.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Admin API: authorization (SUPER_ADMIN only) plus the read dashboards and settings/type mutations.
 * The admin user is seeded at startup by {@link AdminSeeder}; a regular self-registered user must be
 * forbidden from every {@code /admin/**} endpoint.
 */
@AutoConfigureMockMvc
class AdminApiIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${lifeadmin.admin.email}")
    private String adminEmail;
    @Value("${lifeadmin.admin.password}")
    private String adminPassword;

    private String adminToken() throws Exception {
        final var res = mockMvc.perform(post(BASE + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(adminEmail, adminPassword)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("accessToken").asText();
    }

    private String regularUserToken(final String email) throws Exception {
        mockMvc.perform(post(BASE + "/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Reg","email":"%s","password":"ChangeMe123!"}""".formatted(email)))
                .andExpect(status().isCreated());
        final var res = mockMvc.perform(post(BASE + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"ChangeMe123!"}""".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("accessToken").asText();
    }

    @Test
    void regularUserIsForbiddenFromAdminApi() throws Exception {
        final var token = regularUserToken("notadmin@example.com");
        mockMvc.perform(get(BASE + "/admin/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE + "/admin/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminSeesOverviewAndUsers() throws Exception {
        final var token = adminToken();
        mockMvc.perform(get(BASE + "/admin/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalAccounts", greaterThanOrEqualTo(1)));

        mockMvc.perform(get(BASE + "/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[?(@.role == 'SUPER_ADMIN')].email", hasItem(adminEmail)));
    }

    @Test
    void regularUserIsForbiddenFromAnalytics() throws Exception {
        final var token = regularUserToken("noanalytics@example.com");
        mockMvc.perform(get(BASE + "/admin/analytics").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesAnalyticsFunnel() throws Exception {
        // Register a user so signups >= 1; funnel is present with the expected stages and rates.
        regularUserToken("funnel@example.com");
        final var token = adminToken();
        mockMvc.perform(get(BASE + "/admin/analytics").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.funnel[0].stage", org.hamcrest.Matchers.is("Signups")))
                .andExpect(jsonPath("$.funnel[0].count", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.funnel.length()", org.hamcrest.Matchers.is(5)))
                .andExpect(jsonPath("$.retention.length()", org.hamcrest.Matchers.is(3)))
                .andExpect(jsonPath("$.retention[0].days", org.hamcrest.Matchers.is(7)))
                .andExpect(jsonPath("$.processingSuccessRate", notNullValue()))
                .andExpect(jsonPath("$.reminderConversionRate", notNullValue()));
    }

    @Test
    void adminCanReadAndUpdateUploadRules() throws Exception {
        final var token = adminToken();
        mockMvc.perform(get(BASE + "/admin/settings/upload").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedMimeTypes", hasItem("application/pdf")));

        // Restrict uploads to PDF only, 5 MB, 10 pages.
        mockMvc.perform(put(BASE + "/admin/settings/upload").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"allowedMimeTypes":["application/pdf"],"maxFileSizeBytes":5242880,"maxPdfPages":10}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedMimeTypes.length()", org.hamcrest.Matchers.is(1)))
                .andExpect(jsonPath("$.maxPdfPages", org.hamcrest.Matchers.is(10)));

        // Rejects an unsupported MIME type.
        mockMvc.perform(put(BASE + "/admin/settings/upload").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"allowedMimeTypes":["application/zip"]}"""))
                .andExpect(status().isBadRequest());

        // Restore defaults (all three types) — the Testcontainers DB is shared across the suite, so
        // leaving uploads PDF-only would break PNG-upload tests in other classes.
        mockMvc.perform(put(BASE + "/admin/settings/upload").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"allowedMimeTypes":["application/pdf","image/jpeg","image/png"],
                                 "maxFileSizeBytes":10485760,"maxPdfPages":15}"""))
                .andExpect(status().isOk());
    }

    /** Registers a user and returns their id (looked up via the admin users list). */
    private String registerAndGetId(final String email) throws Exception {
        mockMvc.perform(post(BASE + "/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Reg","email":"%s","password":"ChangeMe123!"}""".formatted(email)))
                .andExpect(status().isCreated());
        final var token = adminToken();
        final var res = mockMvc.perform(get(BASE + "/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        final var users = objectMapper.readTree(res).get("users");
        for (final var u : users) {
            if (email.equalsIgnoreCase(u.get("email").asText())) {
                return u.get("id").asText();
            }
        }
        throw new AssertionError("registered user not found in admin users list: " + email);
    }

    @Test
    void adminCanChangeUserRole() throws Exception {
        final var token = adminToken();
        final var id = registerAndGetId("promoteme@example.com");

        // Self-registration always creates OWNER; promote to ADMIN.
        mockMvc.perform(patch(BASE + "/admin/users/" + id + "/role").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ADMIN"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", org.hamcrest.Matchers.is("ADMIN")));
    }

    @Test
    void changingRoleToUnknownValueIsRejected() throws Exception {
        final var token = adminToken();
        final var id = registerAndGetId("badrole@example.com");
        mockMvc.perform(patch(BASE + "/admin/users/" + id + "/role").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"WIZARD"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soleOwnerOfSingleUserAccountCanBeDemoted() throws Exception {
        final var token = adminToken();
        // A freshly registered user is the sole OWNER of a single-user account. The last-owner guard
        // only protects multi-user accounts, so this demotion is allowed (there is no one to orphan).
        final var id = registerAndGetId("soleowner@example.com");
        mockMvc.perform(patch(BASE + "/admin/users/" + id + "/role").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", org.hamcrest.Matchers.is("MEMBER")));
    }

    @Test
    void disablingUserBlocksLoginAndReenablingRestoresIt() throws Exception {
        final var token = adminToken();
        final var email = "disableme@example.com";
        final var id = registerAndGetId(email);

        // Disable → login now fails.
        mockMvc.perform(patch(BASE + "/admin/users/" + id + "/status").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"disabled":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disabled", org.hamcrest.Matchers.is(true)));

        mockMvc.perform(post(BASE + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"ChangeMe123!"}""".formatted(email)))
                .andExpect(status().isUnauthorized());

        // Re-enable → login works again.
        mockMvc.perform(patch(BASE + "/admin/users/" + id + "/status").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"disabled":false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disabled", org.hamcrest.Matchers.is(false)));

        mockMvc.perform(post(BASE + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"ChangeMe123!"}""".formatted(email)))
                .andExpect(status().isOk());
    }

    @Test
    void adminCannotChangeOrDisableTheirOwnAccount() throws Exception {
        final var token = adminToken();
        final var res = mockMvc.perform(get(BASE + "/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        final var users = objectMapper.readTree(res).get("users");
        String adminId = null;
        for (final var u : users) {
            if (adminEmail.equalsIgnoreCase(u.get("email").asText())) {
                adminId = u.get("id").asText();
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(adminId, "admin user should be present");

        mockMvc.perform(patch(BASE + "/admin/users/" + adminId + "/role").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"OWNER"}"""))
                .andExpect(status().isConflict());

        mockMvc.perform(patch(BASE + "/admin/users/" + adminId + "/status").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"disabled":true}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanCreateAndDeleteCustomDocumentType() throws Exception {
        final var token = adminToken();

        // Create a new custom type.
        mockMvc.perform(post(BASE + "/admin/document-types").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeCode":"medical record","label":"Medical Record",
                                 "keywords":"medical,clinic","relevantDateTypes":"APPOINTMENT",
                                 "defaultOffsetsDays":"7"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typeCode", org.hamcrest.Matchers.is("MEDICAL_RECORD")))
                .andExpect(jsonPath("$.label", org.hamcrest.Matchers.is("Medical Record")))
                .andExpect(jsonPath("$.enabled", org.hamcrest.Matchers.is(true)));

        // It now appears in the list.
        mockMvc.perform(get(BASE + "/admin/document-types").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.types[?(@.typeCode == 'MEDICAL_RECORD')].label",
                        hasItem("Medical Record")));

        // Duplicate code → 409.
        mockMvc.perform(post(BASE + "/admin/document-types").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeCode":"MEDICAL_RECORD","label":"Dup"}"""))
                .andExpect(status().isConflict());

        // Delete the custom type → 204.
        mockMvc.perform(delete(BASE + "/admin/document-types/MEDICAL_RECORD")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void createDocumentTypeRejectsBadCodeAndDeleteRejectsBuiltIn() throws Exception {
        final var token = adminToken();

        // Invalid code (starts with a digit) → 400.
        mockMvc.perform(post(BASE + "/admin/document-types").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeCode":"1BAD","label":"Bad"}"""))
                .andExpect(status().isBadRequest());

        // Built-in types cannot be deleted → 409.
        mockMvc.perform(delete(BASE + "/admin/document-types/PASSPORT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanReadAndUpdateDocumentTypes() throws Exception {
        final var token = adminToken();
        mockMvc.perform(get(BASE + "/admin/document-types").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.types[?(@.typeCode == 'INSURANCE')].label", hasItem("Insurance")));

        mockMvc.perform(patch(BASE + "/admin/document-types/INSURANCE").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Insurance Policy","keywords":"insurance,policy,coverage"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label", org.hamcrest.Matchers.is("Insurance Policy")))
                .andExpect(jsonPath("$.keywords", org.hamcrest.Matchers.is("insurance,policy,coverage")));

        // Restore label so other tests/ordering aren't affected (shared container).
        mockMvc.perform(patch(BASE + "/admin/document-types/INSURANCE").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Insurance","keywords":"insurance,policy,insur"}"""))
                .andExpect(status().isOk());
    }
}

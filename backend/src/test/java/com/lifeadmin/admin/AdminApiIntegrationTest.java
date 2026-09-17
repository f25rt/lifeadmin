package com.lifeadmin.admin;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
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

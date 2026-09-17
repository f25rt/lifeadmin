package com.lifeadmin.security.auth;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeadmin.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end auth flow through the full stack: register → login → call the protected {@code /me}
 * with the issued access token. Also covers duplicate-email conflict, refresh-token rotation, and
 * that {@code /me} without a token is rejected. Proves the entities validate against the Flyway
 * schema and the security chain works.
 */
@AutoConfigureMockMvc
@Transactional
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private void register(final String email) throws Exception {
        mockMvc.perform(post(BASE + "/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Juan Dela Cruz","email":"%s","password":"ChangeMe123!"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", notNullValue()))
                .andExpect(jsonPath("$.accountId", notNullValue()));
    }

    private String login(final String email) throws Exception {
        final var res = mockMvc.perform(post(BASE + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"ChangeMe123!"}""".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("accessToken").asText();
    }

    @Test
    void registerLoginThenAccessProtectedMe() throws Exception {
        final var email = "juan@example.com";
        register(email);
        final var accessToken = login(email);

        mockMvc.perform(get(BASE + "/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(email)))
                .andExpect(jsonPath("$.role", is("OWNER")))
                .andExpect(jsonPath("$.plan", is("FREE")));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        register("dupe@example.com");
        mockMvc.perform(post(BASE + "/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Someone Else","email":"dupe@example.com","password":"ChangeMe123!"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")));
    }

    @Test
    void refreshRotatesAndReturnsNewTokens() throws Exception {
        final var email = "refresh@example.com";
        register(email);
        final var loginRes = mockMvc.perform(post(BASE + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"ChangeMe123!"}""".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        final var refreshToken = objectMapper.readTree(loginRes).get("refreshToken").asText();

        // Refresh succeeds once.
        mockMvc.perform(post(BASE + "/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));

        // The old refresh token was rotated (revoked) — reusing it now fails.
        mockMvc.perform(post(BASE + "/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE + "/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidLoginIsUnauthorized() throws Exception {
        register("real@example.com");
        mockMvc.perform(post(BASE + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"real@example.com","password":"WrongPassword1"}"""))
                .andExpect(status().isUnauthorized());
    }
}

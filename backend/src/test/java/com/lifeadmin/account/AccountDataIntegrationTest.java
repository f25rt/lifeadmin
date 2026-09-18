package com.lifeadmin.account;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeadmin.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 5: full-text document search, plan-usage read model, and the self-service data
 * export/delete flow. Storage is the in-memory stub (from {@link AbstractIntegrationTest}); document
 * processing runs inline (messaging disabled in tests).
 */
@AutoConfigureMockMvc
@Transactional
class AccountDataIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

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

    private void uploadPng(final String token, final String fileName) throws Exception {
        final var file = new MockMultipartFile("file", fileName, "image/png", png());
        mockMvc.perform(multipart(BASE + "/documents").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted());
    }

    @Test
    void searchFindsDocumentByTitleAndExcludesNonMatches() throws Exception {
        final var token = registerAndLogin("search@example.com");
        uploadPng(token, "car-insurance-policy.png");
        uploadPng(token, "grocery-receipt.png");

        // Matches the insurance doc's title-derived text, not the receipt.
        mockMvc.perform(get(BASE + "/documents").param("q", "insurance")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].title", is("car-insurance-policy")));

        // A term matching neither returns nothing.
        mockMvc.perform(get(BASE + "/documents").param("q", "zzzznomatch")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    void usageReflectsUploadedDocuments() throws Exception {
        final var token = registerAndLogin("usage@example.com");
        uploadPng(token, "one.png");
        uploadPng(token, "two.png");

        mockMvc.perform(get(BASE + "/account/usage").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan", is("FREE")))
                .andExpect(jsonPath("$.documentsUsed", is(2)))
                .andExpect(jsonPath("$.documentLimit", greaterThanOrEqualTo(2)));
    }

    @Test
    void exportReturnsAccountDataAsJson() throws Exception {
        final var token = registerAndLogin("export@example.com");
        uploadPng(token, "passport.png");

        mockMvc.perform(get(BASE + "/account/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportedAt", notNullValue()))
                .andExpect(jsonPath("$.account.id", notNullValue()))
                .andExpect(jsonPath("$.users[0].email", is("export@example.com")))
                .andExpect(jsonPath("$.documents.length()", is(1)))
                .andExpect(jsonPath("$.documents[0].title", is("passport")));
    }

    @Test
    void deleteAccountRequiresMatchingEmailThenPurgesEverything() throws Exception {
        final var token = registerAndLogin("wipe@example.com");
        uploadPng(token, "doc.png");

        // Wrong confirmation email → 400, nothing deleted.
        mockMvc.perform(delete(BASE + "/account").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmEmail":"nope@example.com"}"""))
                .andExpect(status().isBadRequest());

        // Correct email → 204 and the data is gone.
        mockMvc.perform(delete(BASE + "/account").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmEmail":"wipe@example.com"}"""))
                .andExpect(status().isNoContent());

        // The user no longer exists → their credentials no longer authenticate.
        mockMvc.perform(post(BASE + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"wipe@example.com","password":"ChangeMe123!"}"""))
                .andExpect(status().isUnauthorized());
    }
}

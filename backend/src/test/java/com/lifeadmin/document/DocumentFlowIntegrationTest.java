package com.lifeadmin.document;

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
import java.nio.charset.StandardCharsets;

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
 * End-to-end document flow: register → login → upload a PNG → list → get → download URL → delete.
 * Also covers unsupported content rejected on detected type, and ownership isolation (another
 * account cannot see the document → 404). Storage is the in-memory stub.
 */
@AutoConfigureMockMvc
@Transactional
class DocumentFlowIntegrationTest extends AbstractIntegrationTest {

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

    private String uploadPng(final String token, final String fileName) throws Exception {
        final var file = new MockMultipartFile("file", fileName, "image/png", png());
        final var res = mockMvc.perform(multipart(BASE + "/documents").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("UPLOADED")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("id").asText();
    }

    @Test
    void uploadListGetDownloadDelete() throws Exception {
        final var token = registerAndLogin("doc@example.com");
        final var id = uploadPng(token, "passport.png");

        mockMvc.perform(get(BASE + "/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].title", is("passport")));

        mockMvc.perform(get(BASE + "/documents/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mimeType", is("image/png")))
                .andExpect(jsonPath("$.artifacts[0].kind", is("ORIGINAL")));

        mockMvc.perform(get(BASE + "/documents/" + id + "/download").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", notNullValue()));

        mockMvc.perform(delete(BASE + "/documents/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/documents/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void unsupportedContentIsRejected() throws Exception {
        final var token = registerAndLogin("bad@example.com");
        final var file = new MockMultipartFile("file", "fake.png", "image/png",
                "not an image".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart(BASE + "/documents").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("UNSUPPORTED_MEDIA_TYPE")));
    }

    @Test
    void anotherAccountCannotSeeTheDocument() throws Exception {
        final var ownerToken = registerAndLogin("owner@example.com");
        final var id = uploadPng(ownerToken, "insurance.png");

        final var otherToken = registerAndLogin("intruder@example.com");
        mockMvc.perform(get(BASE + "/documents/" + id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadRequiresAuthentication() throws Exception {
        final var file = new MockMultipartFile("file", "x.png", "image/png", png());
        mockMvc.perform(multipart(BASE + "/documents").file(file))
                .andExpect(status().isUnauthorized());
    }
}

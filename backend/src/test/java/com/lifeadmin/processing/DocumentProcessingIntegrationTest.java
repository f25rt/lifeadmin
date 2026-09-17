package com.lifeadmin.processing;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
 * End-to-end AI-processing flow with the synchronous publisher + stub providers: uploading a file
 * named to look like insurance runs the pipeline inline, so the document lands in REVIEW_REQUIRED
 * with a classified type, extracted fields/dates, and suggested actions. Then verify → ACTIVE.
 */
@AutoConfigureMockMvc
@Transactional
class DocumentProcessingIntegrationTest extends AbstractIntegrationTest {

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

    @Test
    void uploadIsProcessedThenVerifiedToActive() throws Exception {
        final var token = registerAndLogin("proc@example.com");

        // File name hints "insurance" so the stub classifier picks INSURANCE.
        final var file = new MockMultipartFile("file", "my_insurance_policy.png", "image/png", png());
        final var uploadRes = mockMvc.perform(multipart(BASE + "/documents").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        final var id = objectMapper.readTree(uploadRes).get("id").asText();

        // Synchronous publisher processed it inline: REVIEW_REQUIRED + classified + extracted data.
        mockMvc.perform(get(BASE + "/documents/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REVIEW_REQUIRED")))
                .andExpect(jsonPath("$.documentType", is("INSURANCE")))
                .andExpect(jsonPath("$.classificationConfidence", notNullValue()))
                .andExpect(jsonPath("$.fields.length()", greaterThan(0)))
                .andExpect(jsonPath("$.dates.length()", greaterThan(0)))
                .andExpect(jsonPath("$.suggestedActions.length()", greaterThan(0)));

        // Verify with a corrected field → source USER, status ACTIVE.
        mockMvc.perform(post(BASE + "/documents/" + id + "/verify").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentType":"INSURANCE",
                                 "fields":[{"fieldName":"organization","fieldValue":"My Insurer Inc"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        mockMvc.perform(get(BASE + "/documents/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.fields[?(@.fieldName == 'organization')].source", is(java.util.List.of("USER"))))
                .andExpect(jsonPath("$.fields[?(@.fieldName == 'organization')].verified", is(java.util.List.of(true))));
    }

    @Test
    void warrantyUploadHasADerivedDate() throws Exception {
        final var token = registerAndLogin("warranty@example.com");
        final var file = new MockMultipartFile("file", "samsung_warranty_receipt.png", "image/png", png());
        final var id = objectMapper.readTree(
                mockMvc.perform(multipart(BASE + "/documents").file(file).header("Authorization", "Bearer " + token))
                        .andExpect(status().isAccepted())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get(BASE + "/documents/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType", is("WARRANTY")))
                // At least one date is DERIVED (warranty expiration = purchase + term).
                .andExpect(jsonPath("$.dates[?(@.derived == true)].dateType", is(java.util.List.of("WARRANTY_EXPIRATION"))));
    }
}

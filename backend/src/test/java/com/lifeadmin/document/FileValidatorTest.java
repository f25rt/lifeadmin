package com.lifeadmin.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.lifeadmin.admin.settings.AppSettingRepository;
import com.lifeadmin.admin.settings.UploadRulesService;
import com.lifeadmin.common.error.ValidationException;
import com.lifeadmin.security.auth.CurrentUser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileValidatorTest {

    private FileValidator validator;

    /** Builds a FileValidator whose rules come from code defaults (empty settings repo). */
    private static FileValidator validatorWithDefaults(final long maxSize, final int maxPdfPages) {
        final var props = new UploadProperties();
        props.setMaxFileSizeBytes(maxSize);
        props.setMaxPdfPages(maxPdfPages);
        final var settingRepo = mock(AppSettingRepository.class);
        when(settingRepo.findByKey(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        final var rules = new UploadRulesService(settingRepo, props, mock(CurrentUser.class));
        return new FileValidator(rules);
    }

    @BeforeEach
    void setUp() {
        validator = validatorWithDefaults(10 * 1024 * 1024, 15);
    }

    private static byte[] pngBytes() throws Exception {
        final var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        final var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void acceptsPngAndReturnsDetectedMime() throws Exception {
        final var result = validator.validateAndNormalize(pngBytes(), "photo.png");
        assertThat(result.mimeType()).isEqualTo("image/png");
        assertThat(result.storedBytes()).isNotEmpty();
    }

    @Test
    void rejectsUnsupportedContentRegardlessOfExtension() {
        // A text payload named ".png" must be rejected on detected content, not the extension.
        final var textPretendingToBePng = "this is not an image".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> validator.validateAndNormalize(textPretendingToBePng, "fake.png"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> validator.validateAndNormalize(new byte[0], "empty.png"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsOversizedFile() throws Exception {
        final var tiny = validatorWithDefaults(10, 15); // tiny size limit via code default
        assertThatThrownBy(() -> tiny.validateAndNormalize(pngBytes(), "photo.png"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maximum size");
    }
}

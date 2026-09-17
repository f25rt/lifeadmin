package com.lifeadmin.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import com.lifeadmin.common.error.ValidationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileValidatorTest {

    private FileValidator validator;

    @BeforeEach
    void setUp() {
        final var props = new UploadProperties();
        props.setMaxFileSizeBytes(10 * 1024 * 1024);
        props.setMaxPdfPages(15);
        validator = new FileValidator(props);
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
        final var props = new UploadProperties();
        props.setMaxFileSizeBytes(10); // tiny limit
        final var tiny = new FileValidator(props);
        assertThatThrownBy(() -> tiny.validateAndNormalize(pngBytes(), "photo.png"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maximum size");
    }
}

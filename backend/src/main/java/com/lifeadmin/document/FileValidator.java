package com.lifeadmin.document;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import com.lifeadmin.admin.settings.UploadRulesService;
import com.lifeadmin.common.error.ValidationException;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The upload file-safety pipeline (G8):
 * <ul>
 *   <li><b>Magic-byte MIME detection</b> via Tika — the declared content type/extension is not
 *       trusted; the detected type must be in the allow-list.</li>
 *   <li><b>Size</b> guard (defensive; multipart limit also applies).</li>
 *   <li><b>PDF</b> validation: rejects encrypted/malformed PDFs and enforces a max page count.</li>
 *   <li><b>Image</b> normalization: images are re-encoded via ImageIO, which drops EXIF/GPS
 *       metadata (a privacy leak) as a side effect and rejects corrupt images.</li>
 * </ul>
 * Returns the {@link Result}: the canonical (detected) MIME and the bytes to store (re-encoded for
 * images, original for PDFs).
 */
@Component
@RequiredArgsConstructor
public class FileValidator {

    private static final Logger log = LoggerFactory.getLogger(FileValidator.class);

    private static final String PDF = "application/pdf";
    private static final String JPEG = "image/jpeg";
    private static final String PNG = "image/png";
    // Detected MIME -> ImageIO writer format name.
    private static final Map<String, String> IMAGE_FORMAT = Map.of(JPEG, "jpg", PNG, "png");

    private final Tika tika = new Tika();
    private final UploadRulesService uploadRules;

    public record Result(String mimeType, byte[] storedBytes) {
    }

    public Result validateAndNormalize(final byte[] content, final String declaredFileName) {
        if (content == null || content.length == 0) {
            throw new ValidationException("VALIDATION_ERROR", "Uploaded file is empty");
        }
        // Admin-configurable limits (fall back to code defaults when unset).
        final long maxSize = uploadRules.maxFileSizeBytes();
        final Set<String> allowed = uploadRules.allowedMimeTypes();
        if (content.length > maxSize) {
            throw new ValidationException("PAYLOAD_TOO_LARGE",
                    "File exceeds the maximum size of " + maxSize + " bytes");
        }

        final var detected = tika.detect(content);
        if (!allowed.contains(detected)) {
            throw new ValidationException("UNSUPPORTED_MEDIA_TYPE",
                    "Unsupported file type '" + detected + "'. Allowed: " + allowed);
        }

        if (PDF.equals(detected)) {
            validatePdf(content);
            return new Result(PDF, content);
        }
        return new Result(detected, stripImageMetadata(content, detected));
    }

    private void validatePdf(final byte[] content) {
        try (var doc = Loader.loadPDF(content)) {
            if (doc.isEncrypted()) {
                throw new ValidationException("VALIDATION_ERROR", "Encrypted PDFs are not supported");
            }
            final var pages = doc.getNumberOfPages();
            if (pages < 1) {
                throw new ValidationException("VALIDATION_ERROR", "PDF has no pages");
            }
            final int maxPdfPages = uploadRules.maxPdfPages();
            if (pages > maxPdfPages) {
                throw new ValidationException("VALIDATION_ERROR",
                        "PDF has " + pages + " pages; the maximum is " + maxPdfPages);
            }
        } catch (final ValidationException e) {
            throw e;
        } catch (final Exception e) {
            throw new ValidationException("VALIDATION_ERROR", "The PDF could not be read or is malformed");
        }
    }

    /** Re-encodes the image via ImageIO, dropping EXIF/GPS metadata and rejecting corrupt files. */
    private byte[] stripImageMetadata(final byte[] content, final String mimeType) {
        try {
            final var image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new ValidationException("VALIDATION_ERROR", "The image could not be read or is corrupt");
            }
            final var format = IMAGE_FORMAT.get(mimeType);
            final var out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, out)) {
                throw new ValidationException("VALIDATION_ERROR", "Unsupported image encoding");
            }
            return out.toByteArray();
        } catch (final ValidationException e) {
            throw e;
        } catch (final Exception e) {
            log.debug("Image re-encode failed: {}", e.getMessage());
            throw new ValidationException("VALIDATION_ERROR", "The image could not be processed");
        }
    }
}

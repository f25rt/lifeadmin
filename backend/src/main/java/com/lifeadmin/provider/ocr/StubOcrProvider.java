package com.lifeadmin.provider.ocr;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default {@link OcrProvider} used when no cloud OCR is configured (keyless local runs and tests).
 *
 * <p>For PDFs it extracts <em>real</em> text with PDFBox. For images — which need a true OCR engine
 * that a stub can't replicate — it falls back to the file name as a text hint, which is enough for
 * the deterministic stub extractor to classify by keyword and demonstrate the full pipeline. Swap in
 * a real {@code OcrProvider} (cloud Vision / Document AI / Tesseract) by setting
 * {@code lifeadmin.ocr.provider} and providing that bean.
 */
@Component
@ConditionalOnProperty(prefix = "lifeadmin.ocr", name = "provider", havingValue = "stub", matchIfMissing = true)
public class StubOcrProvider implements OcrProvider {

    private static final Logger log = LoggerFactory.getLogger(StubOcrProvider.class);

    @Override
    public String extractText(final byte[] content, final String mimeType, final String fileName) {
        if ("application/pdf".equals(mimeType)) {
            try (var doc = Loader.loadPDF(content)) {
                final var text = new PDFTextStripper().getText(doc);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            } catch (final Exception e) {
                log.debug("Stub OCR: PDF text extraction failed: {}", e.getMessage());
            }
        }
        // Image (or empty PDF): use the file name as a text hint for the stub extractor.
        return fileName == null ? "" : fileName.replace('_', ' ').replace('-', ' ');
    }
}

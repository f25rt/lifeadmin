package com.lifeadmin.provider.ocr;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import jakarta.annotation.PostConstruct;

import net.sourceforge.tess4j.Tesseract;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real OCR via Tesseract (Tess4J/JNA). Active when {@code lifeadmin.ocr.provider=tesseract}.
 *
 * <p>Strategy:
 * <ul>
 *   <li><b>PDF</b> — first try the embedded text layer (PDFBox); digital PDFs need no OCR. If there
 *       is no usable text (a scanned PDF), rasterize each page and OCR the images.</li>
 *   <li><b>Image</b> — decode and OCR directly.</li>
 * </ul>
 *
 * <p>Degrades gracefully: if the native engine or {@code tessdata} is unavailable, or OCR throws, it
 * logs and falls back to the file name as a text hint (same as the stub) so a demo never crashes on
 * an environment without OCR set up. The native Tesseract/Leptonica libraries are bundled by lept4j;
 * only the language data ({@code <lang>.traineddata}) is needed at runtime.
 */
@Component
@ConditionalOnProperty(prefix = "lifeadmin.ocr", name = "provider", havingValue = "tesseract")
public class TesseractOcrProvider implements OcrProvider {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrProvider.class);

    private final OcrProperties properties;
    private volatile String resolvedDatapath;
    private volatile boolean engineAvailable = true;

    public TesseractOcrProvider(final OcrProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        this.resolvedDatapath = resolveDatapath();
        if (resolvedDatapath == null) {
            engineAvailable = false;
            log.warn("Tesseract OCR selected but no tessdata folder was found (set lifeadmin.ocr.datapath "
                    + "or bundle resources/tessdata/{}.traineddata). Falling back to filename hints.",
                    properties.getLanguage());
        } else {
            log.info("Tesseract OCR ready: language='{}', datapath='{}'", properties.getLanguage(), resolvedDatapath);
        }
    }

    @Override
    public String extractText(final byte[] content, final String mimeType, final String fileName) {
        try {
            if ("application/pdf".equals(mimeType)) {
                return extractFromPdf(content, fileName);
            }
            return ocr(decodeImage(content), fileName);
        } catch (final Exception e) {
            log.warn("Tesseract OCR failed for '{}' ({}): {}. Falling back to filename hint.",
                    fileName, mimeType, e.getMessage());
            return fileNameHint(fileName);
        }
    }

    private String extractFromPdf(final byte[] content, final String fileName) throws IOException {
        try (var doc = Loader.loadPDF(content)) {
            final var embedded = new PDFTextStripper().getText(doc);
            if (embedded != null && !embedded.isBlank()) {
                return embedded; // digital PDF — no OCR needed
            }
            if (!engineAvailable) {
                return fileNameHint(fileName);
            }
            // Scanned PDF: rasterize each page and OCR it.
            final var renderer = new PDFRenderer(doc);
            final var out = new StringBuilder();
            for (int page = 0; page < doc.getNumberOfPages(); page++) {
                final BufferedImage image = renderer.renderImageWithDPI(page, properties.getPdfRenderDpi());
                final var pageText = ocr(image, null);
                if (pageText != null && !pageText.isBlank()) {
                    out.append(pageText).append('\n');
                }
            }
            final var text = out.toString();
            return text.isBlank() ? fileNameHint(fileName) : text;
        }
    }

    /** Runs Tesseract on one image; on any failure returns the filename hint. */
    private String ocr(final BufferedImage image, final String fileName) {
        if (!engineAvailable || image == null) {
            return fileNameHint(fileName);
        }
        try {
            final var tesseract = new Tesseract();
            tesseract.setDatapath(resolvedDatapath);
            tesseract.setLanguage(properties.getLanguage());
            final var text = tesseract.doOCR(image);
            return text == null ? "" : text.trim();
        } catch (final Throwable t) {
            // Throwable: JNA can raise UnsatisfiedLinkError/NoClassDefFoundError if natives are missing.
            engineAvailable = false;
            log.warn("Tesseract engine unavailable ({}); disabling OCR and using filename hints.",
                    t.getMessage());
            return fileNameHint(fileName);
        }
    }

    private static BufferedImage decodeImage(final byte[] content) throws IOException {
        try (var in = new ByteArrayInputStream(content)) {
            return ImageIO.read(in);
        }
    }

    private static String fileNameHint(final String fileName) {
        return fileName == null ? "" : fileName.replace('_', ' ').replace('-', ' ');
    }

    /**
     * Resolve a tessdata folder. Prefers the configured {@code datapath}; otherwise looks for a
     * {@code tessdata} folder on the classpath. If that lives inside a jar, the language file is
     * copied to a temp directory (Tesseract needs a real filesystem path). Returns {@code null} if
     * none can be found.
     */
    private String resolveDatapath() {
        final var configured = properties.getDatapath();
        if (configured != null && !configured.isBlank()) {
            if (Files.isDirectory(Path.of(configured))) {
                return configured;
            }
            log.warn("Configured lifeadmin.ocr.datapath '{}' is not a directory", configured);
        }
        try {
            final URL url = getClass().getClassLoader().getResource("tessdata");
            if (url == null) {
                return null;
            }
            if ("file".equals(url.getProtocol())) {
                return Path.of(url.toURI()).toString();
            }
            // Inside a jar: extract the traineddata to a temp tessdata dir.
            return extractBundledTessdata();
        } catch (final Exception e) {
            log.warn("Could not resolve bundled tessdata: {}", e.getMessage());
            return null;
        }
    }

    private String extractBundledTessdata() throws IOException {
        final var lang = properties.getLanguage();
        final var candidates = new ArrayList<String>();
        for (final var l : lang.split("\\+")) {
            candidates.add("tessdata/" + l + ".traineddata");
        }
        final var tmpDir = Files.createTempDirectory("lifeadmin-tessdata");
        final var tessdata = Files.createDirectories(tmpDir.resolve("tessdata"));
        boolean any = false;
        for (final var resource : candidates) {
            try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                if (stream == null) {
                    continue;
                }
                final var name = resource.substring(resource.lastIndexOf('/') + 1);
                Files.copy(stream, tessdata.resolve(name));
                any = true;
            }
        }
        if (!any) {
            return null;
        }
        tmpDir.toFile().deleteOnExit();
        return tessdata.toString();
    }
}

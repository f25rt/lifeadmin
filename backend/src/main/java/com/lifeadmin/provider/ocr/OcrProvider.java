package com.lifeadmin.provider.ocr;

/**
 * Extracts text from a document image/PDF (spec §24). Behind an interface so the concrete engine
 * (cloud Vision / Document AI / Tesseract / stub) is swappable without touching the pipeline.
 */
public interface OcrProvider {

    /**
     * @param content   the document bytes
     * @param mimeType  detected content type (e.g. {@code image/png}, {@code application/pdf})
     * @param fileName  original file name (a hint some engines/stubs use)
     * @return extracted plain text (may be empty)
     */
    String extractText(byte[] content, String mimeType, String fileName);
}

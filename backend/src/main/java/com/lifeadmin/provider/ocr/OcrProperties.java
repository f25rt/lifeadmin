package com.lifeadmin.provider.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code lifeadmin.ocr.*}. {@code provider} selects the {@link OcrProvider} bean
 * ({@code stub} default, or {@code tesseract}). The remaining values configure Tesseract:
 * {@code datapath} points at the folder containing {@code <lang>.traineddata} (blank = auto-detect a
 * bundled {@code tessdata} on the classpath), {@code language} is the Tesseract language code, and
 * {@code pdfRenderDpi} is the resolution used when rasterizing image-only PDF pages for OCR.
 */
@ConfigurationProperties(prefix = "lifeadmin.ocr")
public class OcrProperties {

    private String provider = "stub";
    private String datapath = "";
    private String language = "eng";
    private int pdfRenderDpi = 300;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getDatapath() { return datapath; }
    public void setDatapath(String datapath) { this.datapath = datapath; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public int getPdfRenderDpi() { return pdfRenderDpi; }
    public void setPdfRenderDpi(int pdfRenderDpi) { this.pdfRenderDpi = pdfRenderDpi; }
}

package com.lifeadmin.document;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code lifeadmin.upload.*} — upload guards (G4/G8). */
@ConfigurationProperties(prefix = "lifeadmin.upload")
public class UploadProperties {

    private long maxFileSizeBytes = 10 * 1024 * 1024; // 10 MB
    private int maxPdfPages = 15;

    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }

    public int getMaxPdfPages() { return maxPdfPages; }
    public void setMaxPdfPages(int maxPdfPages) { this.maxPdfPages = maxPdfPages; }
}

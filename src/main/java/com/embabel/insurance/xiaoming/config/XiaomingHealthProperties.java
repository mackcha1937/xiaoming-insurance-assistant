package com.embabel.insurance.xiaoming.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xiaoming.health")
public class XiaomingHealthProperties {

    private int maxFiles = 5;
    private long maxFileBytes = 10L * 1024 * 1024;
    private int maxPdfPages = 30;
    private int maxTextCharacters = 20_000;
    private double minimumConfidence = 0.5;
    private long processingTimeoutMillis = 30_000;

    public int getMaxFiles() { return maxFiles; }
    public void setMaxFiles(int maxFiles) { this.maxFiles = maxFiles; }
    public long getMaxFileBytes() { return maxFileBytes; }
    public void setMaxFileBytes(long maxFileBytes) { this.maxFileBytes = maxFileBytes; }
    public int getMaxPdfPages() { return maxPdfPages; }
    public void setMaxPdfPages(int maxPdfPages) { this.maxPdfPages = maxPdfPages; }
    public int getMaxTextCharacters() { return maxTextCharacters; }
    public void setMaxTextCharacters(int maxTextCharacters) { this.maxTextCharacters = maxTextCharacters; }
    public double getMinimumConfidence() { return minimumConfidence; }
    public void setMinimumConfidence(double minimumConfidence) { this.minimumConfidence = minimumConfidence; }
    public long getProcessingTimeoutMillis() { return processingTimeoutMillis; }
    public void setProcessingTimeoutMillis(long processingTimeoutMillis) {
        if (processingTimeoutMillis < 1) throw new IllegalArgumentException("processingTimeoutMillis must be positive");
        this.processingTimeoutMillis = processingTimeoutMillis;
    }
}

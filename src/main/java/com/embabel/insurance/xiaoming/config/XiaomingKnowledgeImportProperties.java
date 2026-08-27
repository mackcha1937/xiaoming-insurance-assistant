package com.embabel.insurance.xiaoming.config;

import com.embabel.insurance.xiaoming.knowledge.importer.SpreadsheetScanMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xiaoming.knowledge-import")
public record XiaomingKnowledgeImportProperties(
        boolean enabled,
        String sourceDirectory,
        SpreadsheetScanMode spreadsheetScanMode
) {
    public XiaomingKnowledgeImportProperties {
        spreadsheetScanMode = spreadsheetScanMode == null
                ? SpreadsheetScanMode.STRUCTURE_ONLY
                : spreadsheetScanMode;
    }
}

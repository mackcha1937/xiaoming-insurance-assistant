package com.embabel.insurance.xiaoming.health;

import java.util.List;

public record HealthExtractionResult(
        String extractionVersion,
        String knowledgeVersion,
        List<HealthFactDraft> facts,
        List<HealthFileResult> files,
        String notice
) {
    public HealthExtractionResult {
        facts = facts == null ? List.of() : List.copyOf(facts);
        files = files == null ? List.of() : List.copyOf(files);
    }
}

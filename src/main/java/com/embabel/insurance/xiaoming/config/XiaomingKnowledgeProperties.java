package com.embabel.insurance.xiaoming.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "xiaoming.knowledge")
public record XiaomingKnowledgeProperties(
        @Min(100) @Max(10000) int maxChunkCharacters,
        @Min(0) @Max(9999) int chunkOverlapCharacters,
        @Min(1) @Max(1000) int rrfK,
        @DecimalMin("0.0") @DecimalMax("1.0") double evidenceThreshold,
        @DecimalMin("0.0") @DecimalMax("1.0") double degradedVectorThreshold
) {
    public XiaomingKnowledgeProperties {
        if (chunkOverlapCharacters >= maxChunkCharacters) {
            throw new IllegalArgumentException("chunk overlap must be smaller than max chunk size");
        }
    }
}

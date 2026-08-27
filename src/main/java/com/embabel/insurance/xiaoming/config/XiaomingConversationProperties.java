package com.embabel.insurance.xiaoming.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "xiaoming.conversation")
public record XiaomingConversationProperties(
        boolean modelEnabled,
        @Min(1) @Max(20) int topK,
        @Min(200) @Max(12000) int maxEvidenceCharacters,
        @Min(100) @Max(4000) int maxAnswerCharacters
) {
}

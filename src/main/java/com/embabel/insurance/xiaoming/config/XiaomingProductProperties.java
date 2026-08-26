package com.embabel.insurance.xiaoming.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "xiaoming.product")
public record XiaomingProductProperties(
        @NotBlank String productId,
        @NotBlank String name,
        @NotBlank String currentKnowledgeVersion,
        @NotBlank String environment
) {
}

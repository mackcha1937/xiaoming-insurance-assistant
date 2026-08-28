package com.embabel.insurance.xiaoming.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "xiaoming.application")
public record XiaomingApplicationProperties(
        boolean enabled,
        String baseUrl,
        List<String> allowedDomains,
        List<String> allowedParams,
        long ttlSeconds
) {
    public XiaomingApplicationProperties {
        baseUrl = baseUrl == null ? "" : baseUrl.trim();
        allowedDomains = allowedDomains == null ? List.of() : allowedDomains.stream()
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
        allowedParams = allowedParams == null ? List.of() : allowedParams.stream()
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
        ttlSeconds = ttlSeconds <= 0 ? 300 : Math.min(ttlSeconds, 900);
    }
}

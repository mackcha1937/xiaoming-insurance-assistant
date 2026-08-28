package com.embabel.insurance.xiaoming.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xiaoming.feedback")
public record XiaomingFeedbackProperties(boolean enabled, int maxRecords) {
    public XiaomingFeedbackProperties {
        maxRecords = maxRecords <= 0 ? 1000 : Math.min(maxRecords, 10_000);
    }
}

package com.embabel.insurance.xiaoming.rules;

import java.util.List;

public record BusinessRule(
        String ruleId,
        String productId,
        String version,
        String domain,
        ConditionNode condition,
        String result,
        int priority,
        List<String> evidenceIds
) {
    public BusinessRule {
        requireText(ruleId, "ruleId");
        requireText(productId, "productId");
        requireText(version, "version");
        requireText(domain, "domain");
        requireText(result, "result");
        if (condition == null) {
            throw new IllegalArgumentException("condition must not be null");
        }
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}

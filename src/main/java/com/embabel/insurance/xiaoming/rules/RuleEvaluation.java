package com.embabel.insurance.xiaoming.rules;

import java.util.Set;

public record RuleEvaluation(
        BusinessRule rule,
        RuleMatchStatus status,
        Set<String> missingFields
) {
    public RuleEvaluation {
        missingFields = missingFields == null ? Set.of() : Set.copyOf(missingFields);
    }
}

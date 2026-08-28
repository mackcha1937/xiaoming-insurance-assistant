package com.embabel.insurance.xiaoming.health;

import com.embabel.insurance.xiaoming.contract.XiaomingEvidence;

import java.util.List;

public record HealthQuestionMatch(
        String questionId,
        String questionText,
        HealthMatchState state,
        List<String> matchedFactIds,
        String reason,
        List<String> missingFields,
        XiaomingEvidence evidence
) {
    public HealthQuestionMatch {
        matchedFactIds = matchedFactIds == null ? List.of() : List.copyOf(matchedFactIds);
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
    }
}

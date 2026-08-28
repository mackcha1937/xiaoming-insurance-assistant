package com.embabel.insurance.xiaoming.health;

import java.util.List;

public record HealthMatchResult(
        String matchVersion,
        String knowledgeVersion,
        HealthMatchState state,
        List<HealthQuestionMatch> questions,
        String notice
) {
    public HealthMatchResult {
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}

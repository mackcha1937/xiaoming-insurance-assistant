package com.embabel.insurance.xiaoming.conversation;

import com.embabel.insurance.xiaoming.contract.XiaomingEvidence;
import com.embabel.insurance.xiaoming.contract.XiaomingIntent;

import java.util.List;

public record AnswerRequest(
        XiaomingIntent intent,
        String question,
        List<String> ruleResults,
        List<XiaomingEvidence> evidence,
        int maxEvidenceCharacters
) {
    public AnswerRequest {
        ruleResults = ruleResults == null ? List.of() : List.copyOf(ruleResults);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}

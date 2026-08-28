package com.embabel.insurance.xiaoming.feedback;

import com.embabel.insurance.xiaoming.contract.XiaomingIntent;

import java.time.Instant;
import java.util.List;

public record FeedbackRecord(
        String feedbackId,
        String productId,
        String anonymousConversationId,
        String knowledgeVersion,
        XiaomingIntent intent,
        FeedbackReason reason,
        String questionSummary,
        String answerSummary,
        List<String> evidenceIds,
        Instant acceptedAt
) {
    public FeedbackRecord {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}

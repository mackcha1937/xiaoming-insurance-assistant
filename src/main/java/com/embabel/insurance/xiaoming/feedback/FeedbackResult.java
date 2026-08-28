package com.embabel.insurance.xiaoming.feedback;

import java.time.Instant;

public record FeedbackResult(String feedbackId, String status, Instant acceptedAt) {
}

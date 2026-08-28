package com.embabel.insurance.xiaoming.feedback;

import java.util.List;

public interface FeedbackStore {
    void save(FeedbackRecord record);
    List<FeedbackRecord> snapshot();
}

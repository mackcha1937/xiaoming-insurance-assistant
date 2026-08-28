package com.embabel.insurance.xiaoming.feedback;

import com.embabel.insurance.xiaoming.config.XiaomingFeedbackProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Component
public class InMemoryFeedbackStore implements FeedbackStore {

    private final Deque<FeedbackRecord> records = new ArrayDeque<>();
    private final int maxRecords;

    public InMemoryFeedbackStore(XiaomingFeedbackProperties properties) {
        this.maxRecords = properties.maxRecords();
    }

    @Override
    public synchronized void save(FeedbackRecord record) {
        while (records.size() >= maxRecords) records.removeFirst();
        records.addLast(record);
    }

    @Override
    public synchronized List<FeedbackRecord> snapshot() {
        return List.copyOf(new ArrayList<>(records));
    }
}

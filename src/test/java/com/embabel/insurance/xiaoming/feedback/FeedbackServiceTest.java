package com.embabel.insurance.xiaoming.feedback;

import com.embabel.insurance.xiaoming.api.FeedbackRequest;
import com.embabel.insurance.xiaoming.config.XiaomingFeedbackProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedbackServiceTest {

    @Test
    void storesOnlyAnonymousExplicitlyIncludedSafeSummaries() {
        List<FeedbackRecord> saved = new ArrayList<>();
        FeedbackStore store = new FeedbackStore() {
            @Override public void save(FeedbackRecord record) { saved.add(record); }
            @Override public List<FeedbackRecord> snapshot() { return List.copyOf(saved); }
        };
        FeedbackService service = new FeedbackService(
                new XiaomingFeedbackProperties(true, 10), store, new SimpleMeterRegistry());
        FeedbackRequest request = request("COVERAGE_RESPONSIBILITY", true);
        request.setQuestionSummary("这项保障的等待期是多少？");
        request.setAnswerSummary("回答中的等待期与引用不一致。");

        FeedbackResult result = service.submit(request);

        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().anonymousConversationId()).startsWith("anon-")
                .doesNotContain("conversation-private");
        assertThat(saved.getFirst().questionSummary()).isEqualTo("这项保障的等待期是多少？");
    }

    @Test
    void defaultsToNoContextAndRejectsHealthOrSensitiveSummaries() {
        FeedbackStore store = new FeedbackStore() {
            private final List<FeedbackRecord> records = new ArrayList<>();
            @Override public void save(FeedbackRecord record) { records.add(record); }
            @Override public List<FeedbackRecord> snapshot() { return List.copyOf(records); }
        };
        FeedbackService service = new FeedbackService(
                new XiaomingFeedbackProperties(true, 10), store, new SimpleMeterRegistry());
        FeedbackRequest defaultRequest = request("APPLICATION_RULES", false);
        defaultRequest.setQuestionSummary("this must be ignored");
        service.submit(defaultRequest);
        assertThat(store.snapshot().getFirst().questionSummary()).isNull();

        FeedbackRequest health = request("HEALTH_DISCLOSURE", true);
        assertThatThrownBy(() -> service.submit(health)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("健康告知反馈");

        FeedbackRequest sensitive = request("APPLICATION_RULES", true);
        sensitive.setQuestionSummary("请联系 13812345678");
        assertThatThrownBy(() -> service.submit(sensitive)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("敏感");

        FeedbackRequest identity = request("APPLICATION_RULES", true);
        identity.setAnswerSummary("联系人：张三，家庭地址：幸福路 1 号");
        assertThatThrownBy(() -> service.submit(identity)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("敏感");
    }

    private FeedbackRequest request(String intent, boolean includeContext) {
        FeedbackRequest request = new FeedbackRequest();
        request.setProductId("36743");
        request.setConversationId("conversation-private-0001");
        request.setKnowledgeVersion("knowledge-v1");
        request.setIntent(intent);
        request.setReason("EVIDENCE_MISMATCH");
        request.setIncludeContext(includeContext);
        request.setEvidenceIds(List.of("evidence-1"));
        return request;
    }
}

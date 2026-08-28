package com.embabel.insurance.xiaoming.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FeedbackRequest {

    @NotBlank @Pattern(regexp = "36743", message = "当前仅支持产品 36743")
    private String productId;

    @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}", message = "conversationId 格式无效")
    private String conversationId;

    @NotBlank @Size(max = 64)
    private String knowledgeVersion;

    @NotBlank @Pattern(regexp = "COVERAGE_RESPONSIBILITY|EXCLUSIONS_AND_LIMITATIONS|APPLICATION_RULES|OCCUPATION_QUERY|APPLICATION_PROCESS|MATERIAL_CHECKLIST|HEALTH_DISCLOSURE|CLAIMS_PATH|FORMAL_APPLICATION")
    private String intent;

    @NotBlank @Pattern(regexp = "EVIDENCE_MISMATCH|RULE_MISMATCH|UNCLEAR|OUTDATED|OTHER")
    private String reason;

    private boolean includeContext;

    @Size(max = 240)
    private String questionSummary;

    @Size(max = 500)
    private String answerSummary;

    @Size(max = 10)
    private List<@Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}") String> evidenceIds;

    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getKnowledgeVersion() { return knowledgeVersion; }
    public void setKnowledgeVersion(String knowledgeVersion) { this.knowledgeVersion = knowledgeVersion; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public boolean isIncludeContext() { return includeContext; }
    public void setIncludeContext(boolean includeContext) { this.includeContext = includeContext; }
    public String getQuestionSummary() { return questionSummary; }
    public void setQuestionSummary(String questionSummary) { this.questionSummary = questionSummary; }
    public String getAnswerSummary() { return answerSummary; }
    public void setAnswerSummary(String answerSummary) { this.answerSummary = answerSummary; }
    public List<String> getEvidenceIds() { return evidenceIds == null ? List.of() : List.copyOf(evidenceIds); }
    public void setEvidenceIds(List<String> evidenceIds) { this.evidenceIds = evidenceIds; }

    @JsonAnySetter
    public void captureUnknownField(String name, Object value) { unknownFields.put(name, value); }
    public boolean hasUnknownFields() { return !unknownFields.isEmpty(); }

    public Map<String, Object> fingerprintPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", productId);
        payload.put("conversationId", conversationId);
        payload.put("knowledgeVersion", knowledgeVersion);
        payload.put("intent", intent);
        payload.put("reason", reason);
        payload.put("includeContext", includeContext);
        if (questionSummary != null) payload.put("questionSummary", questionSummary);
        if (answerSummary != null) payload.put("answerSummary", answerSummary);
        payload.put("evidenceIds", getEvidenceIds());
        return Map.copyOf(payload);
    }
}

package com.embabel.insurance.xiaoming.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HealthMatchRequest {

    @NotBlank(message = "productId 不能为空")
    @Pattern(regexp = "36743", message = "当前仅支持产品 36743")
    private String productId;

    @NotBlank(message = "conversationId 不能为空")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}", message = "conversationId 格式无效")
    private String conversationId;

    @NotBlank(message = "knowledgeVersion 不能为空")
    @Size(max = 64, message = "knowledgeVersion 不能超过 64 个字符")
    private String knowledgeVersion;

    private boolean confirmed;

    @Min(value = 0, message = "insuredAgeYears 不能小于 0")
    @Max(value = 120, message = "insuredAgeYears 不能大于 120")
    private Integer insuredAgeYears;

    @Pattern(regexp = "MALE|FEMALE|UNSPECIFIED", message = "insuredSex 无效")
    private String insuredSex;

    @Valid
    @NotEmpty(message = "facts 不能为空")
    @Size(max = 100, message = "facts 不能超过 100 项")
    private List<HealthFactRequest> facts;

    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getKnowledgeVersion() { return knowledgeVersion; }
    public void setKnowledgeVersion(String knowledgeVersion) { this.knowledgeVersion = knowledgeVersion; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
    public Integer getInsuredAgeYears() { return insuredAgeYears; }
    public void setInsuredAgeYears(Integer insuredAgeYears) { this.insuredAgeYears = insuredAgeYears; }
    public String getInsuredSex() { return insuredSex; }
    public void setInsuredSex(String insuredSex) { this.insuredSex = insuredSex; }
    public List<HealthFactRequest> getFacts() { return facts; }
    public void setFacts(List<HealthFactRequest> facts) { this.facts = facts; }

    @JsonAnySetter
    public void captureUnknownField(String name, Object value) { unknownFields.put(name, value); }

    public boolean hasUnknownFields() {
        return !unknownFields.isEmpty() || facts != null && facts.stream().anyMatch(HealthFactRequest::hasUnknownFields);
    }

    public Map<String, Object> fingerprintPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", productId);
        payload.put("conversationId", conversationId);
        payload.put("knowledgeVersion", knowledgeVersion);
        payload.put("confirmed", confirmed);
        if (insuredAgeYears != null) payload.put("insuredAgeYears", insuredAgeYears);
        if (insuredSex != null) payload.put("insuredSex", insuredSex);
        payload.put("facts", facts);
        return Map.copyOf(payload);
    }
}

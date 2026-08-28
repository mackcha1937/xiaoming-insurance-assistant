package com.embabel.insurance.xiaoming.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

public class ChecklistGenerateRequest {

    @NotBlank(message = "productId 不能为空")
    @Pattern(regexp = "36743", message = "当前仅支持产品 36743")
    private String productId;

    @NotBlank(message = "conversationId 不能为空")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}", message = "conversationId 格式无效")
    private String conversationId;

    @NotBlank(message = "knowledgeVersion 不能为空")
    @Size(max = 64, message = "knowledgeVersion 不能超过 64 个字符")
    private String knowledgeVersion;

    @NotNull(message = "insuredAgeYears 不能为空")
    @Min(value = 0, message = "insuredAgeYears 不能小于 0")
    @Max(value = 120, message = "insuredAgeYears 不能大于 120")
    private Integer insuredAgeYears;

    @NotBlank(message = "applicantRelationship 不能为空")
    @Pattern(regexp = "SELF|PARENT|CHILD|SPOUSE|OTHER", message = "applicantRelationship 无效")
    private String applicantRelationship;

    @NotBlank(message = "idDocumentType 不能为空")
    @Pattern(regexp = "NATIONAL_ID|PASSPORT|OTHER", message = "idDocumentType 无效")
    private String idDocumentType;

    @NotBlank(message = "beneficiaryMode 不能为空")
    @Pattern(regexp = "STATUTORY|DESIGNATED", message = "beneficiaryMode 无效")
    private String beneficiaryMode;

    @NotBlank(message = "healthStatus 不能为空")
    @Pattern(regexp = "NOT_CHECKED|NO_EXCEPTION|NEEDS_DISCLOSURE", message = "healthStatus 无效")
    private String healthStatus;

    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getKnowledgeVersion() { return knowledgeVersion; }
    public void setKnowledgeVersion(String knowledgeVersion) { this.knowledgeVersion = knowledgeVersion; }
    public Integer getInsuredAgeYears() { return insuredAgeYears; }
    public void setInsuredAgeYears(Integer insuredAgeYears) { this.insuredAgeYears = insuredAgeYears; }
    public String getApplicantRelationship() { return applicantRelationship; }
    public void setApplicantRelationship(String applicantRelationship) { this.applicantRelationship = applicantRelationship; }
    public String getIdDocumentType() { return idDocumentType; }
    public void setIdDocumentType(String idDocumentType) { this.idDocumentType = idDocumentType; }
    public String getBeneficiaryMode() { return beneficiaryMode; }
    public void setBeneficiaryMode(String beneficiaryMode) { this.beneficiaryMode = beneficiaryMode; }
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }

    @JsonAnySetter
    public void captureUnknownField(String name, Object value) { unknownFields.put(name, value); }
    public boolean hasUnknownFields() { return !unknownFields.isEmpty(); }

    public Map<String, Object> fingerprintPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", productId);
        payload.put("conversationId", conversationId);
        payload.put("knowledgeVersion", knowledgeVersion);
        payload.put("insuredAgeYears", insuredAgeYears);
        payload.put("applicantRelationship", applicantRelationship);
        payload.put("idDocumentType", idDocumentType);
        payload.put("beneficiaryMode", beneficiaryMode);
        payload.put("healthStatus", healthStatus);
        return Map.copyOf(payload);
    }
}

package com.embabel.insurance.xiaoming.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

public class OccupationConfirmRequest {

    @NotBlank(message = "productId 不能为空")
    @Pattern(regexp = "36743", message = "当前仅支持产品 36743")
    private String productId;

    @NotBlank(message = "occupationId 不能为空")
    @Pattern(regexp = "[0-9]{8,32}", message = "occupationId 格式无效")
    private String occupationId;

    @NotBlank(message = "knowledgeVersion 不能为空")
    @Size(max = 64, message = "knowledgeVersion 不能超过 64 个字符")
    private String knowledgeVersion;

    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getOccupationId() {
        return occupationId;
    }

    public void setOccupationId(String occupationId) {
        this.occupationId = occupationId;
    }

    public String getKnowledgeVersion() {
        return knowledgeVersion;
    }

    public void setKnowledgeVersion(String knowledgeVersion) {
        this.knowledgeVersion = knowledgeVersion;
    }

    @JsonAnySetter
    public void captureUnknownField(String name, Object value) {
        unknownFields.put(name, value);
    }

    public boolean hasUnknownFields() {
        return !unknownFields.isEmpty();
    }

    public Map<String, Object> fingerprintPayload() {
        return Map.of(
                "productId", productId,
                "occupationId", occupationId,
                "knowledgeVersion", knowledgeVersion
        );
    }
}

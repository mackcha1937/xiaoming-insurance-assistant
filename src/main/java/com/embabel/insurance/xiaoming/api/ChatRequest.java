package com.embabel.insurance.xiaoming.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

public class ChatRequest {

    @NotBlank(message = "productId 不能为空")
    @Pattern(regexp = "36743", message = "当前仅支持产品 36743")
    private String productId;

    @NotBlank(message = "conversationId 不能为空")
    @Pattern(
            regexp = "[A-Za-z0-9][A-Za-z0-9._-]{7,63}",
            message = "conversationId 格式无效"
    )
    private String conversationId;

    @NotBlank(message = "message 不能为空")
    @Size(max = 2000, message = "message 不能超过 2000 个字符")
    private String message;

    private Map<String, Object> context = Map.of();

    @Size(max = 64, message = "knowledgeVersionHint 不能超过 64 个字符")
    private String knowledgeVersionHint;

    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    public String getKnowledgeVersionHint() {
        return knowledgeVersionHint;
    }

    public void setKnowledgeVersionHint(String knowledgeVersionHint) {
        this.knowledgeVersionHint = knowledgeVersionHint;
    }

    @JsonAnySetter
    public void captureUnknownField(String name, Object value) {
        unknownFields.put(name, value);
    }

    public boolean hasUnknownFields() {
        return !unknownFields.isEmpty();
    }

    public Map<String, Object> fingerprintPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", productId);
        payload.put("conversationId", conversationId);
        payload.put("message", message);
        payload.put("context", context);
        payload.put("knowledgeVersionHint", knowledgeVersionHint);
        return payload;
    }
}

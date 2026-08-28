package com.embabel.insurance.xiaoming.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class HealthFactRequest {

    @NotBlank(message = "factId 不能为空")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}", message = "factId 格式无效")
    private String factId;

    @NotBlank(message = "type 不能为空")
    @Pattern(regexp = "DISEASE|SYMPTOM|HOSPITALIZATION|SURGERY|MEDICATION|EXAMINATION|MEASUREMENT|FOLLOW_UP|CURRENT_STATUS|ACTIVITY|BIRTH_HISTORY|PREGNANCY", message = "type 无效")
    private String type;

    @NotBlank(message = "code 不能为空")
    @Size(max = 64, message = "code 不能超过 64 个字符")
    private String code;

    @NotBlank(message = "label 不能为空")
    @Size(max = 80, message = "label 不能超过 80 个字符")
    private String label;

    @NotBlank(message = "value 不能为空")
    @Size(max = 240, message = "value 不能超过 240 个字符")
    private String value;

    private Double numericValue;

    @Size(max = 24, message = "unit 不能超过 24 个字符")
    private String unit;

    private LocalDate date;

    @NotBlank(message = "polarity 不能为空")
    @Pattern(regexp = "PRESENT|ABSENT|UNKNOWN", message = "polarity 无效")
    private String polarity;

    @Valid
    @NotNull(message = "sourceLocation 不能为空")
    private HealthSourceLocationRequest sourceLocation;

    @DecimalMin(value = "0.0", message = "confidence 不能小于 0")
    @DecimalMax(value = "1.0", message = "confidence 不能大于 1")
    private double confidence;

    private boolean confirmed;

    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public String getFactId() { return factId; }
    public void setFactId(String factId) { this.factId = factId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public Double getNumericValue() { return numericValue; }
    public void setNumericValue(Double numericValue) { this.numericValue = numericValue; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getPolarity() { return polarity; }
    public void setPolarity(String polarity) { this.polarity = polarity; }
    public HealthSourceLocationRequest getSourceLocation() { return sourceLocation; }
    public void setSourceLocation(HealthSourceLocationRequest sourceLocation) { this.sourceLocation = sourceLocation; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }

    @JsonAnySetter
    public void captureUnknownField(String name, Object value) { unknownFields.put(name, value); }
    public boolean hasUnknownFields() {
        return !unknownFields.isEmpty() || sourceLocation != null && sourceLocation.hasUnknownFields();
    }

    public static class HealthSourceLocationRequest {
        @NotBlank(message = "sourceId 不能为空")
        @Size(max = 128, message = "sourceId 不能超过 128 个字符")
        private String sourceId;

        @NotBlank(message = "sourceLabel 不能为空")
        @Size(max = 80, message = "sourceLabel 不能超过 80 个字符")
        private String sourceLabel;

        private Integer page;
        private Integer line;

        @NotBlank(message = "excerpt 不能为空")
        @Size(max = 240, message = "excerpt 不能超过 240 个字符")
        private String excerpt;

        private final Map<String, Object> unknownFields = new LinkedHashMap<>();

        public String getSourceId() { return sourceId; }
        public void setSourceId(String sourceId) { this.sourceId = sourceId; }
        public String getSourceLabel() { return sourceLabel; }
        public void setSourceLabel(String sourceLabel) { this.sourceLabel = sourceLabel; }
        public Integer getPage() { return page; }
        public void setPage(Integer page) { this.page = page; }
        public Integer getLine() { return line; }
        public void setLine(Integer line) { this.line = line; }
        public String getExcerpt() { return excerpt; }
        public void setExcerpt(String excerpt) { this.excerpt = excerpt; }
        @JsonAnySetter
        public void captureUnknownField(String name, Object value) { unknownFields.put(name, value); }
        public boolean hasUnknownFields() { return !unknownFields.isEmpty(); }
    }
}

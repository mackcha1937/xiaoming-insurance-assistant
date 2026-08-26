package com.embabel.insurance.xiaoming.contract;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "达尔文12号限定域九类业务意图")
public enum XiaomingIntent {
    COVERAGE_RESPONSIBILITY("保障责任"),
    EXCLUSIONS_AND_LIMITATIONS("免责与限制"),
    APPLICATION_RULES("投保规则"),
    OCCUPATION_QUERY("职业查询"),
    APPLICATION_PROCESS("投保流程"),
    MATERIAL_CHECKLIST("动态资料清单"),
    HEALTH_DISCLOSURE("健康告知"),
    CLAIMS_PATH("理赔路径"),
    FORMAL_APPLICATION("正式投保");

    private final String label;

    XiaomingIntent(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

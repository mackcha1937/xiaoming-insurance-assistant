package com.embabel.insurance.xiaoming.contract;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "小明统一业务状态")
public enum BusinessStatus {
    SUCCESS,
    NEED_INPUT,
    INSUFFICIENT_EVIDENCE,
    REFUSED,
    CONFLICT,
    ERROR
}

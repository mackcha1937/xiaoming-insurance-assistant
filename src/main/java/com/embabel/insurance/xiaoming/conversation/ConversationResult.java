package com.embabel.insurance.xiaoming.conversation;

import com.embabel.insurance.xiaoming.contract.XiaomingResponse;
import org.springframework.http.HttpStatus;

import java.util.Map;

public record ConversationResult(
        HttpStatus httpStatus,
        XiaomingResponse<Map<String, Object>> response
) {
}

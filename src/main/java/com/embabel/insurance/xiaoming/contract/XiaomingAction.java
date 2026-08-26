package com.embabel.insurance.xiaoming.contract;

import java.util.Map;

public record XiaomingAction(
        String id,
        ActionType type,
        String label,
        boolean primary,
        boolean enabled,
        String disabledReason,
        Map<String, Object> payload
) {
    public XiaomingAction {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}

package com.embabel.insurance.xiaoming.contract;

import java.util.Map;

public record XiaomingCard(
        String id,
        String schemaVersion,
        CardType type,
        String title,
        Map<String, Object> data
) {
    public XiaomingCard {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}

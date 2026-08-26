package com.embabel.insurance.xiaoming.contract;

public record XiaomingSuggestion(
        String id,
        String label,
        String prompt,
        XiaomingIntent intent
) {
}

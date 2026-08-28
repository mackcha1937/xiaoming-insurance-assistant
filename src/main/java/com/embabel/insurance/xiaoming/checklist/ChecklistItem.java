package com.embabel.insurance.xiaoming.checklist;

public record ChecklistItem(
        String id,
        ChecklistSection section,
        String label,
        boolean required,
        String reason
) {
}

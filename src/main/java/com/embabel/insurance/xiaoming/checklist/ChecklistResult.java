package com.embabel.insurance.xiaoming.checklist;

import java.util.List;

public record ChecklistResult(
        String checklistVersion,
        String knowledgeVersion,
        List<ChecklistItem> items,
        String notice
) {
    public ChecklistResult {
        items = List.copyOf(items);
    }
}

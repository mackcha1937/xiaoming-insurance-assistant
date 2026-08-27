package com.embabel.insurance.xiaoming.knowledge.importer;

public record OccupationEntry(
        String occupationId,
        String templateId,
        String bigCategory,
        String secondCategory,
        String minorCategory,
        String fourthCategory,
        int classCode,
        boolean declined
) {
}

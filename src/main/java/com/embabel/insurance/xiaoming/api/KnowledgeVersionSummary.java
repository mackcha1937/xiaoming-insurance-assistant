package com.embabel.insurance.xiaoming.api;

import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionStatus;

import java.time.Instant;

public record KnowledgeVersionSummary(
        String productId,
        String knowledgeVersion,
        KnowledgeVersionStatus status,
        String rulesVersion,
        String occupationVersion,
        Instant publishedAt,
        int documentCount,
        boolean formalSourcesLoaded
) {
}

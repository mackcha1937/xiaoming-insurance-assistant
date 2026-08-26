package com.embabel.insurance.xiaoming.evidence;

import com.embabel.insurance.xiaoming.contract.XiaomingEvidence;
import com.embabel.insurance.xiaoming.retrieval.RankedChunk;
import com.embabel.insurance.xiaoming.retrieval.RetrievalResult;
import com.embabel.insurance.xiaoming.rules.RuleEvaluation;
import com.embabel.insurance.xiaoming.rules.RuleMatchStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EvidenceService {

    public EvidenceDecision evaluate(
            String productId,
            String knowledgeVersion,
            RetrievalResult retrieval,
            List<RuleEvaluation> ruleEvaluations
    ) {
        List<RankedChunk> eligible = retrieval.chunks().stream()
                .filter(chunk -> chunk.score() >= retrieval.evidenceThreshold())
                .toList();
        if (eligible.isEmpty()) {
            return decision(
                    EvidenceGateStatus.INSUFFICIENT,
                    "没有达到证据阈值的正式片段。",
                    List.of(),
                    List.of(),
                    retrieval
            );
        }
        if (eligible.stream().anyMatch(chunk -> !matchesScope(chunk, productId, knowledgeVersion))) {
            return decision(
                    EvidenceGateStatus.CONFLICT,
                    "证据包含其他产品或知识版本。",
                    List.of(),
                    List.of(),
                    retrieval
            );
        }
        if (eligible.stream().anyMatch(this::incomplete)) {
            return decision(
                    EvidenceGateStatus.INSUFFICIENT,
                    "证据缺少文件、章节、页码或原文。",
                    List.of(),
                    List.of(),
                    retrieval
            );
        }

        List<RuleEvaluation> matchedRules = ruleEvaluations.stream()
                .filter(evaluation -> evaluation.status() == RuleMatchStatus.MATCHED)
                .toList();
        if (hasRuleConflict(matchedRules)) {
            return decision(
                    EvidenceGateStatus.CONFLICT,
                    "同优先级规则返回冲突结果。",
                    List.of(),
                    matchedRules.stream().map(evaluation -> evaluation.rule().ruleId()).toList(),
                    retrieval
            );
        }

        Set<String> availableEvidenceIds = eligible.stream()
                .map(chunk -> chunk.chunk().chunkId())
                .collect(Collectors.toSet());
        List<String> missingRuleEvidence = matchedRules.stream()
                .flatMap(evaluation -> evaluation.rule().evidenceIds().stream())
                .filter(evidenceId -> !availableEvidenceIds.contains(evidenceId))
                .distinct()
                .toList();
        if (!missingRuleEvidence.isEmpty()) {
            return decision(
                    EvidenceGateStatus.INSUFFICIENT,
                    "规则缺少对应的正式原文依据。",
                    List.of(),
                    matchedRules.stream().map(evaluation -> evaluation.rule().ruleId()).toList(),
                    retrieval
            );
        }

        List<XiaomingEvidence> evidence = eligible.stream()
                .map(chunk -> new XiaomingEvidence(
                        chunk.chunk().chunkId(),
                        chunk.chunk().documentName(),
                        chunk.chunk().section(),
                        chunk.chunk().pdfPage(),
                        chunk.chunk().printedPage(),
                        chunk.chunk().text(),
                        chunk.chunk().knowledgeVersion()
                ))
                .toList();
        return decision(
                EvidenceGateStatus.SUFFICIENT,
                retrieval.degraded() ? "证据门禁通过，检索处于安全降级模式。" : "证据门禁通过。",
                evidence,
                matchedRules.stream().map(evaluation -> evaluation.rule().ruleId()).toList(),
                retrieval
        );
    }

    private boolean matchesScope(RankedChunk result, String productId, String version) {
        return result.chunk().productId().equals(productId)
                && result.chunk().knowledgeVersion().equals(version);
    }

    private boolean incomplete(RankedChunk result) {
        return result.chunk().documentName().isBlank()
                || result.chunk().section().isBlank()
                || result.chunk().pdfPage() < 1
                || result.chunk().text().isBlank();
    }

    private boolean hasRuleConflict(List<RuleEvaluation> evaluations) {
        Map<String, Set<String>> resultsByDomainAndPriority = evaluations.stream()
                .collect(Collectors.groupingBy(
                        evaluation -> evaluation.rule().domain() + ":" + evaluation.rule().priority(),
                        Collectors.mapping(
                                evaluation -> evaluation.rule().result(),
                                Collectors.toCollection(LinkedHashSet::new)
                        )
                ));
        return resultsByDomainAndPriority.values().stream().anyMatch(results -> results.size() > 1);
    }

    private EvidenceDecision decision(
            EvidenceGateStatus status,
            String reason,
            List<XiaomingEvidence> evidence,
            List<String> ruleIds,
            RetrievalResult retrieval
    ) {
        return new EvidenceDecision(
                status,
                reason,
                evidence,
                ruleIds,
                retrieval.mode(),
                retrieval.degraded()
        );
    }
}

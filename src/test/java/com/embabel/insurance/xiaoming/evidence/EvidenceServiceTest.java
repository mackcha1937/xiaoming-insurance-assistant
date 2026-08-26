package com.embabel.insurance.xiaoming.evidence;

import com.embabel.insurance.xiaoming.knowledge.ClauseChunk;
import com.embabel.insurance.xiaoming.knowledge.DocumentType;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeFixtures;
import com.embabel.insurance.xiaoming.retrieval.RankedChunk;
import com.embabel.insurance.xiaoming.retrieval.RetrievalMode;
import com.embabel.insurance.xiaoming.retrieval.RetrievalResult;
import com.embabel.insurance.xiaoming.rules.RuleEvaluation;
import com.embabel.insurance.xiaoming.rules.RuleMatchStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceServiceTest {

    private final EvidenceService service = new EvidenceService();

    @Test
    void createsTraceableEvidenceOnlyAfterGatePasses() {
        EvidenceDecision decision = service.evaluate(
                KnowledgeFixtures.PRODUCT_ID,
                KnowledgeFixtures.VERSION,
                retrieval(KnowledgeFixtures.chunk("evidence-1")),
                List.of(matchedRule("rule-1", "ALLOW", 10, List.of("evidence-1")))
        );

        assertEquals(EvidenceGateStatus.SUFFICIENT, decision.status());
        assertEquals(1, decision.evidence().size());
        assertEquals("正式保险条款.pdf", decision.evidence().getFirst().documentName());
        assertEquals(3, decision.evidence().getFirst().pdfPage());
        assertEquals(1, decision.evidence().getFirst().printedPage());
        assertEquals(KnowledgeFixtures.VERSION, decision.evidence().getFirst().knowledgeVersion());
        assertEquals(List.of("rule-1"), decision.ruleIds());
    }

    @Test
    void stopsWhenNoEvidenceReachesThreshold() {
        RetrievalResult retrieval = new RetrievalResult(
                List.of(new RankedChunk(KnowledgeFixtures.chunk("low"), 0.001, true, false)),
                RetrievalMode.BM25_ONLY,
                true,
                0.01
        );

        EvidenceDecision decision = service.evaluate(
                KnowledgeFixtures.PRODUCT_ID,
                KnowledgeFixtures.VERSION,
                retrieval,
                List.of()
        );

        assertEquals(EvidenceGateStatus.INSUFFICIENT, decision.status());
        assertTrue(decision.evidence().isEmpty());
    }

    @Test
    void stopsOnCrossProductOrOldVersionEvidence() {
        ClauseChunk otherProduct = KnowledgeFixtures.chunk(
                "other", "other", KnowledgeFixtures.VERSION, DocumentType.POLICY_TERMS
        );
        ClauseChunk oldVersion = KnowledgeFixtures.chunk(
                "old", KnowledgeFixtures.PRODUCT_ID, "old", DocumentType.POLICY_TERMS
        );

        assertEquals(
                EvidenceGateStatus.CONFLICT,
                service.evaluate(
                        KnowledgeFixtures.PRODUCT_ID,
                        KnowledgeFixtures.VERSION,
                        retrieval(otherProduct),
                        List.of()
                ).status()
        );
        assertEquals(
                EvidenceGateStatus.CONFLICT,
                service.evaluate(
                        KnowledgeFixtures.PRODUCT_ID,
                        KnowledgeFixtures.VERSION,
                        retrieval(oldVersion),
                        List.of()
                ).status()
        );
    }

    @Test
    void stopsWhenMatchedRuleEvidenceIsMissing() {
        EvidenceDecision decision = service.evaluate(
                KnowledgeFixtures.PRODUCT_ID,
                KnowledgeFixtures.VERSION,
                retrieval(KnowledgeFixtures.chunk("available")),
                List.of(matchedRule("rule-1", "ALLOW", 10, List.of("required")))
        );

        assertEquals(EvidenceGateStatus.INSUFFICIENT, decision.status());
        assertTrue(decision.evidence().isEmpty());
    }

    @Test
    void stopsWhenSamePriorityRulesConflict() {
        EvidenceDecision decision = service.evaluate(
                KnowledgeFixtures.PRODUCT_ID,
                KnowledgeFixtures.VERSION,
                retrieval(KnowledgeFixtures.chunk("evidence-1")),
                List.of(
                        matchedRule("allow", "ALLOW", 10, List.of("evidence-1")),
                        matchedRule("deny", "DENY", 10, List.of("evidence-1"))
                )
        );

        assertEquals(EvidenceGateStatus.CONFLICT, decision.status());
        assertTrue(decision.evidence().isEmpty());
    }

    @Test
    void differentPrioritiesAreNotReportedAsAConflict() {
        EvidenceDecision decision = service.evaluate(
                KnowledgeFixtures.PRODUCT_ID,
                KnowledgeFixtures.VERSION,
                retrieval(KnowledgeFixtures.chunk("evidence-1")),
                List.of(
                        matchedRule("high", "ALLOW", 10, List.of("evidence-1")),
                        matchedRule("low", "DENY", 1, List.of("evidence-1"))
                )
        );

        assertEquals(EvidenceGateStatus.SUFFICIENT, decision.status());
    }

    @Test
    void propagatesSafeDegradationStatus() {
        RetrievalResult degraded = new RetrievalResult(
                List.of(new RankedChunk(KnowledgeFixtures.chunk("evidence-1"), 0.02, true, false)),
                RetrievalMode.BM25_ONLY,
                true,
                0.01
        );

        EvidenceDecision decision = service.evaluate(
                KnowledgeFixtures.PRODUCT_ID,
                KnowledgeFixtures.VERSION,
                degraded,
                List.of()
        );

        assertEquals(EvidenceGateStatus.SUFFICIENT, decision.status());
        assertTrue(decision.degraded());
        assertEquals(RetrievalMode.BM25_ONLY, decision.retrievalMode());
    }

    private RetrievalResult retrieval(ClauseChunk chunk) {
        return new RetrievalResult(
                List.of(new RankedChunk(chunk, 0.02, true, true)),
                RetrievalMode.HYBRID,
                false,
                0.01
        );
    }

    private RuleEvaluation matchedRule(
            String id,
            String result,
            int priority,
            List<String> evidenceIds
    ) {
        return new RuleEvaluation(
                KnowledgeFixtures.rule(id, result, priority, evidenceIds),
                RuleMatchStatus.MATCHED,
                Set.of()
        );
    }
}

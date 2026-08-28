package com.embabel.insurance.xiaoming.conversation;

import com.embabel.insurance.xiaoming.api.ChatRequest;
import com.embabel.insurance.xiaoming.config.XiaomingConversationProperties;
import com.embabel.insurance.xiaoming.config.XiaomingKnowledgeProperties;
import com.embabel.insurance.xiaoming.contract.BusinessStatus;
import com.embabel.insurance.xiaoming.contract.XiaomingCard;
import com.embabel.insurance.xiaoming.contract.XiaomingEvidence;
import com.embabel.insurance.xiaoming.contract.XiaomingIntent;
import com.embabel.insurance.xiaoming.contract.XiaomingResponse;
import com.embabel.insurance.xiaoming.evidence.EvidenceService;
import com.embabel.insurance.xiaoming.guardrail.IntentGuardService;
import com.embabel.insurance.xiaoming.knowledge.SemanticChunker;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersion;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionStatus;
import com.embabel.insurance.xiaoming.knowledge.importer.ImportedKnowledgeBundle;
import com.embabel.insurance.xiaoming.knowledge.importer.KnowledgeSnapshotStore;
import com.embabel.insurance.xiaoming.knowledge.importer.Product36743SourceCatalog;
import com.embabel.insurance.xiaoming.knowledge.importer.Product36743SourceImporter;
import com.embabel.insurance.xiaoming.knowledge.importer.SpreadsheetScanMode;
import com.embabel.insurance.xiaoming.retrieval.RrfFusionService;
import com.embabel.insurance.xiaoming.rules.RuleEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase08StandardQuestionAcceptanceTest {

    private static final Path SOURCE_DIRECTORY = Path.of("docs/source-materials/product-36743/raw");
    private StandardQuestionSuite suite;
    private XiaomingConversationService service;

    @BeforeAll
    void preparePublishedKnowledgeAndVersionedQuestionSet() throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.isRegularFile(SOURCE_DIRECTORY.resolve("01-主险条款.pdf")),
                "confirmed local source materials are not present"
        );
        try (InputStream input = getClass().getResourceAsStream("/xiaoming/phase08-standard-questions.json")) {
            assertNotNull(input, "versioned standard-question resource must exist");
            suite = new ObjectMapper().readValue(input, StandardQuestionSuite.class);
        }
        ImportedKnowledgeBundle bundle = new Product36743SourceImporter(new SemanticChunker(1_200, 150))
                .importFrom(SOURCE_DIRECTORY, SpreadsheetScanMode.STRUCTURE_ONLY);
        KnowledgeSnapshotStore store = new KnowledgeSnapshotStore();
        KnowledgeVersion published = bundle.knowledgeVersion()
                .withStatus(KnowledgeVersionStatus.PUBLISHED, Instant.parse("2026-08-27T12:00:00Z"));
        store.replace(published, bundle);
        service = new XiaomingConversationService(
                new IntentGuardService(),
                store,
                new RuleEngine(),
                new EvidenceService(),
                new RrfFusionService(),
                new XiaomingKnowledgeProperties(1_200, 150, 60, 0.01, 0.8),
                new XiaomingConversationProperties(false, 10, 6_000, 1_200),
                new ConversationFactExtractor(),
                new EvidenceOnlyAnswerGenerator(),
                new OutputGuardService()
        );
    }

    @Test
    void questionSetHasFrozenCoverageAndNoSensitiveFixtures() {
        assertEquals("phase08-standard-questions-v1", suite.suiteVersion());
        assertEquals(Product36743SourceCatalog.PRODUCT_ID, suite.productId());
        assertEquals(Product36743SourceCatalog.KNOWLEDGE_VERSION, suite.knowledgeVersion());
        assertEquals(40, suite.cases().size());
        assertEquals(40, suite.cases().stream().map(StandardQuestionCase::caseId).distinct().count());

        Map<String, Long> categoryCounts = suite.cases().stream()
                .collect(Collectors.groupingBy(StandardQuestionCase::category, Collectors.counting()));
        for (XiaomingIntent intent : XiaomingIntent.values()) {
            assertTrue(categoryCounts.getOrDefault(intent.name(), 0L) >= 3,
                    () -> intent + " must have at least three standard questions");
        }
        long boundaryCases = suite.cases().stream()
                .filter(item -> item.category().equals("BOUNDARY_REFUSAL")
                        || item.category().equals("PROMPT_INJECTION")
                        || item.category().equals("MIXED_BOUNDARY"))
                .count();
        assertTrue(boundaryCases >= 8);
        suite.cases().forEach(item -> {
            String fixture = item.question() + item.context();
            assertFalse(fixture.matches(".*1[3-9]\\d{9}.*"), item.caseId() + " contains a mobile number");
            assertFalse(fixture.matches(".*\\d{17}[0-9Xx].*"), item.caseId() + " contains an identity number");
            assertFalse(fixture.contains("http://") || fixture.contains("https://"),
                    item.caseId() + " contains a URL");
        });
    }

    @Test
    void allFortyQuestionsPassIntentStatusEvidenceRuleAndBoundaryAssertions() {
        for (StandardQuestionCase questionCase : suite.cases()) {
            ConversationResult result = service.answer(
                    request(questionCase),
                    "trace-" + questionCase.caseId(),
                    "request-" + questionCase.caseId()
            );
            XiaomingResponse<Map<String, Object>> response = result.response();
            String label = questionCase.caseId() + ": " + questionCase.question();

            assertEquals(BusinessStatus.valueOf(questionCase.expectedStatus()), response.status(), label);
            if (questionCase.expectedIntent() == null) {
                assertNull(response.intent(), label);
            } else {
                assertEquals(XiaomingIntent.valueOf(questionCase.expectedIntent()), response.intent(), label);
            }
            assertEquals(suite.knowledgeVersion(), response.knowledgeVersion(), label);

            if (questionCase.expectedTarget() != null) {
                assertEquals(questionCase.expectedTarget(), response.data().get("target"), label);
            }
            if (questionCase.expectedPartial()) {
                assertEquals(Boolean.TRUE, response.data().get("partial"), label);
                assertTrue(response.data().containsKey("boundaryNotice"), label);
            }
            if (questionCase.requireEvidence()) {
                assertFalse(response.evidence().isEmpty(), label + " must have evidence");
                assertTrue(response.evidence().stream().allMatch(this::completeEvidence),
                        label + " contains incomplete evidence");
                assertTrue(questionCase.expectedDocumentFragments().stream().anyMatch(fragment ->
                                response.evidence().stream().map(XiaomingEvidence::documentName)
                                        .anyMatch(name -> name.contains(fragment))),
                        () -> label + " expected one of " + questionCase.expectedDocumentFragments()
                                + " but got " + response.evidence().stream()
                                .map(XiaomingEvidence::documentName).distinct().toList());
            } else {
                assertTrue(response.evidence().isEmpty(), label + " must not expose evidence");
            }

            Set<String> actualRuleIds = response.cards().stream()
                    .map(XiaomingCard::data)
                    .map(data -> data.get("ruleIds"))
                    .filter(List.class::isInstance)
                    .flatMap(value -> ((List<?>) value).stream())
                    .map(String::valueOf)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            assertTrue(actualRuleIds.containsAll(questionCase.expectedRuleIds()),
                    () -> label + " expected rules " + questionCase.expectedRuleIds() + " but got " + actualRuleIds);

            String rendered = renderedText(response);
            questionCase.requiredAnswerTerms().forEach(term ->
                    assertTrue(rendered.contains(term), () -> label + " missing required term: " + term));
            questionCase.forbiddenTerms().forEach(term ->
                    assertFalse(rendered.contains(term), () -> label + " contains forbidden term: " + term));
        }
    }

    @Test
    void ordinaryEvidenceBackedQuestionP95IsBelowEightSeconds() {
        List<StandardQuestionCase> ordinaryQuestions = suite.cases().stream()
                .filter(item -> item.expectedStatus().equals("SUCCESS"))
                .filter(item -> !item.expectedPartial())
                .toList();
        assertTrue(ordinaryQuestions.size() >= 10);
        ordinaryQuestions.stream().limit(5).forEach(item ->
                service.answer(request(item), "warmup-" + item.caseId(), "warmup-" + item.caseId()));

        List<Long> durations = new ArrayList<>();
        for (int round = 0; round < 10; round++) {
            for (StandardQuestionCase item : ordinaryQuestions) {
                long started = System.nanoTime();
                ConversationResult result = service.answer(
                        request(item),
                        "perf-trace-" + round + "-" + item.caseId(),
                        "perf-request-" + round + "-" + item.caseId()
                );
                durations.add(System.nanoTime() - started);
                assertEquals(BusinessStatus.SUCCESS, result.response().status(), item.caseId());
            }
        }
        durations.sort(Comparator.naturalOrder());
        long p50 = percentile(durations, 0.50);
        long p95 = percentile(durations, 0.95);
        long max = durations.getLast();
        System.out.printf(
                "[phase08-performance] ordinary-qa samples=%d p50=%.3fms p95=%.3fms max=%.3fms%n",
                durations.size(), millis(p50), millis(p95), millis(max)
        );
        assertTrue(p95 < 8_000_000_000L, "ordinary question P95 must be below eight seconds");
    }

    private ChatRequest request(StandardQuestionCase questionCase) {
        ChatRequest request = new ChatRequest();
        request.setProductId(suite.productId());
        request.setConversationId("phase08-" + questionCase.caseId());
        request.setMessage(questionCase.question());
        request.setContext(questionCase.context());
        request.setKnowledgeVersionHint(suite.knowledgeVersion());
        return request;
    }

    private boolean completeEvidence(XiaomingEvidence evidence) {
        return evidence.evidenceId() != null && !evidence.evidenceId().isBlank()
                && evidence.documentName() != null && !evidence.documentName().isBlank()
                && evidence.section() != null && !evidence.section().isBlank()
                && evidence.pdfPage() != null && evidence.pdfPage() > 0
                && evidence.quote() != null && !evidence.quote().isBlank()
                && suite.knowledgeVersion().equals(evidence.knowledgeVersion());
    }

    private String renderedText(XiaomingResponse<Map<String, Object>> response) {
        return response.message() + " " + response.cards().stream()
                .map(card -> card.data().values().stream().map(String::valueOf).collect(Collectors.joining(" ")))
                .collect(Collectors.joining(" "));
    }

    private long percentile(List<Long> sorted, double percentile) {
        int index = Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1);
        return sorted.get(index);
    }

    private double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    record StandardQuestionSuite(
            String suiteVersion,
            String productId,
            String knowledgeVersion,
            List<StandardQuestionCase> cases
    ) {
    }

    record StandardQuestionCase(
            String caseId,
            String category,
            String question,
            Map<String, Object> context,
            String expectedStatus,
            String expectedIntent,
            boolean expectedPartial,
            boolean requireEvidence,
            String expectedTarget,
            List<String> expectedDocumentFragments,
            List<String> expectedRuleIds,
            List<String> requiredAnswerTerms,
            List<String> forbiddenTerms
    ) {
        StandardQuestionCase {
            context = context == null ? Map.of() : Map.copyOf(context);
            expectedDocumentFragments = expectedDocumentFragments == null
                    ? List.of() : List.copyOf(expectedDocumentFragments);
            expectedRuleIds = expectedRuleIds == null ? List.of() : List.copyOf(expectedRuleIds);
            requiredAnswerTerms = requiredAnswerTerms == null ? List.of() : List.copyOf(requiredAnswerTerms);
            forbiddenTerms = forbiddenTerms == null ? List.of() : List.copyOf(forbiddenTerms);
        }
    }
}

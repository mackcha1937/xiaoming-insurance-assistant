package com.embabel.insurance.xiaoming.knowledge.importer;

import com.embabel.insurance.xiaoming.knowledge.DocumentType;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionStatus;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionRegistry;
import com.embabel.insurance.xiaoming.knowledge.SemanticChunker;
import com.embabel.insurance.xiaoming.config.XiaomingProductProperties;
import com.embabel.insurance.xiaoming.retrieval.InMemoryBm25SearchBackend;
import com.embabel.insurance.xiaoming.retrieval.SearchFilter;
import com.embabel.insurance.xiaoming.retrieval.SearchHit;
import com.embabel.insurance.xiaoming.rules.RuleEngine;
import com.embabel.insurance.xiaoming.rules.RuleMatchStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Product36743SourceImportAcceptanceTest {

    private static final Path SOURCE_DIRECTORY = Path.of("docs/source-materials/product-36743/raw");
    private ImportedKnowledgeBundle bundle;

    @BeforeAll
    void importConfirmedSources() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.isRegularFile(SOURCE_DIRECTORY.resolve("01-主险条款.pdf")),
                "confirmed local source materials are not present"
        );
        bundle = new Product36743SourceImporter(new SemanticChunker(1_200, 150))
                .importFrom(SOURCE_DIRECTORY, SpreadsheetScanMode.STRUCTURE_ONLY);
    }

    @Test
    void importsAllConfirmedSourcesWithStablePagesAndStructuredDatasets() {
        assertEquals(21, bundle.knowledgeVersion().documents().size());
        assertEquals(50, bundle.knowledgeVersion().documents().stream()
                .filter(document -> document.documentId().equals("doc-01"))
                .findFirst().orElseThrow().pageMap().size());
        assertTrue(bundle.chunks().size() > 250);
        assertEquals(1_546, bundle.occupations().size());
        assertEquals(Map.of(1, 358L, 2, 423L, 3, 398L, 4, 367L),
                bundle.occupations().stream().collect(java.util.stream.Collectors.groupingBy(
                        OccupationEntry::classCode,
                        java.util.stream.Collectors.counting()
                )));
        assertEquals(List.of(4, 8, 1, 1), bundle.spreadsheets().stream()
                .map(summary -> summary.sheets().size())
                .toList());
        Set<String> chunkIds = bundle.chunks().stream()
                .map(chunk -> chunk.chunkId())
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(bundle.rules().size() >= 19);
        assertTrue(bundle.rules().stream()
                .flatMap(rule -> rule.evidenceIds().stream())
                .allMatch(chunkIds::contains));
        assertTrue(new Product36743ReleaseValidator().validate(
                bundle.knowledgeVersion().withStatus(KnowledgeVersionStatus.REVIEW, null),
                bundle
        ).isEmpty());
    }

    @Test
    void structuredRulesPreserveOpenAgeBoundaryAndKeyNumericLimits() {
        RuleEngine engine = new RuleEngine();
        assertEquals(RuleMatchStatus.MATCHED, status(engine, "applicant-age", Map.of("applicantAgeYears", 54)));
        assertEquals(RuleMatchStatus.NOT_MATCHED, status(engine, "applicant-age", Map.of("applicantAgeYears", 55)));
        assertEquals(RuleMatchStatus.MATCHED, status(engine, "insured-age-lifetime-10", Map.of(
                "coverageTerm", "LIFETIME", "paymentYears", 10, "insuredAgeYears", 50
        )));
        assertEquals(RuleMatchStatus.NOT_MATCHED, status(engine, "insured-age-lifetime-10", Map.of(
                "coverageTerm", "LIFETIME", "paymentYears", 10, "insuredAgeYears", 51
        )));
        assertEquals(RuleMatchStatus.MATCHED, status(engine, "occupation-class-allowed", Map.of("occupationClass", 4)));
        assertEquals(RuleMatchStatus.MATCHED, status(engine, "occupation-class-disallowed", Map.of("occupationClass", 5)));
        assertTrue(bundle.rules().stream()
                .filter(rule -> rule.ruleId().equals("age-41-45"))
                .findFirst().orElseThrow().result().contains("MAX_BASIC_SUM_INSURED_CNY=300000"));
    }

    @Test
    void publishesTheImportedSnapshotOnlyAfterReleaseValidation() {
        Product36743SourceImporter preparedImporter = new Product36743SourceImporter(new SemanticChunker(1_200, 150)) {
            @Override
            public ImportedKnowledgeBundle importFrom(Path sourceDirectory, SpreadsheetScanMode spreadsheetScanMode) {
                return bundle;
            }
        };
        KnowledgeVersionRegistry registry = new KnowledgeVersionRegistry(
                new XiaomingProductProperties("36743", "达尔文12号", "baseline", "test"),
                Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC)
        );
        KnowledgeSnapshotStore snapshotStore = new KnowledgeSnapshotStore();
        Product36743KnowledgeImportService service = new Product36743KnowledgeImportService(
                preparedImporter,
                new Product36743ReleaseValidator(),
                registry,
                snapshotStore
        );

        KnowledgeSnapshotStore.Snapshot snapshot = service.importAndPublish(
                SOURCE_DIRECTORY,
                SpreadsheetScanMode.STRUCTURE_ONLY
        );

        assertEquals(Product36743SourceCatalog.KNOWLEDGE_VERSION, registry.current().versionId());
        assertEquals(KnowledgeVersionStatus.PUBLISHED, snapshot.knowledgeVersion().status());
        assertEquals(21, snapshot.knowledgeVersion().documents().size());
        assertEquals(bundle.chunks().size(), snapshot.chunks().size());
    }

    @Test
    void retrievesFortyKeywordAndColloquialRegressionQuestionsFromExpectedSources() {
        InMemoryBm25SearchBackend backend = new InMemoryBm25SearchBackend(bundle.chunks());
        List<QueryCase> cases = standardQuestions();
        assertEquals(40, cases.size());
        for (QueryCase queryCase : cases) {
            List<SearchHit> hits = backend.search(
                    queryCase.query(),
                    new SearchFilter(
                            Product36743SourceCatalog.PRODUCT_ID,
                            Product36743SourceCatalog.KNOWLEDGE_VERSION,
                            queryCase.documentTypes()
                    ),
                    10
            );
            assertFalse(hits.isEmpty(), () -> "no result for: " + queryCase.query());
            assertTrue(hits.stream().anyMatch(hit -> queryCase.expectedDocumentIds().contains(hit.chunk().documentId())),
                    () -> "expected source not found for: " + queryCase.query() + ", got "
                            + hits.stream().map(hit -> hit.chunk().documentId()).toList());
        }
    }

    private RuleMatchStatus status(RuleEngine engine, String ruleId, Map<String, Object> facts) {
        return engine.evaluate(
                        Product36743SourceCatalog.PRODUCT_ID,
                        Product36743SourceCatalog.KNOWLEDGE_VERSION,
                        bundle.rules().stream().filter(rule -> rule.ruleId().equals(ruleId)).toList(),
                        facts
                ).getFirst().status();
    }

    private List<QueryCase> standardQuestions() {
        Set<DocumentType> rules = Set.of(DocumentType.APPLICATION_RULES, DocumentType.APPLICATION_GUIDE);
        Set<DocumentType> terms = Set.of(DocumentType.POLICY_TERMS);
        Set<DocumentType> health = Set.of(DocumentType.HEALTH_DISCLOSURE);
        return List.of(
                q("投保人年龄有什么要求", rules, "doc-06", "doc-20"),
                q("我55岁还能当投保人吗", rules, "doc-06", "doc-20"),
                q("投保人必须是成年人吗", rules, "doc-06", "doc-20"),
                q("谁可以给孩子买这份保险", rules, "doc-06", "doc-20"),
                q("被保险人最大能到几岁", rules, "doc-06", "doc-12", "doc-20"),
                q("十年交最高投保年龄", rules, "doc-12", "doc-06"),
                q("三十年缴费年龄限制", rules, "doc-12", "doc-06"),
                q("保到70岁几岁之前可以买", rules, "doc-12", "doc-06"),
                q("哪些职业可以买", rules, "doc-06", "doc-20"),
                q("五类职业能投保吗", rules, "doc-06"),
                q("职业类别按照哪张表", rules, "doc-06"),
                q("一到四类职业是否承保", rules, "doc-06", "doc-20"),
                q("基本保额最高可以买多少", rules, "doc-11", "doc-06"),
                q("45岁累计重疾保额上限", rules, "doc-11", "doc-06"),
                q("51岁最多能买多少保额", rules, "doc-11", "doc-06"),
                q("风险保额是怎么算的", rules, "doc-11", "doc-06"),
                q("保终身有哪些可选责任", rules, "doc-13", "doc-06"),
                q("保到70岁能不能选癌症津贴", rules, "doc-13", "doc-06"),
                q("疾病关爱金责任年龄限制", rules, "doc-12", "doc-06"),
                q("重大疾病多次给付能否二选一", rules, "doc-06", "doc-13"),
                q("等待期多少天", rules, "doc-07", "doc-20"),
                q("合同生效多久后才过等待期", rules, "doc-07", "doc-20"),
                q("犹豫期是几天", rules, "doc-07", "doc-20"),
                q("签收电子保单后多久能无损退", rules, "doc-07", "doc-20"),
                q("理赔需要经过哪些步骤", rules, "doc-07", "doc-20"),
                q("怎么报案申请保险金", rules, "doc-07", "doc-20"),
                q("理赔客服电话是多少", rules, "doc-07", "doc-20"),
                q("线上理赔在哪里提交", rules, "doc-07", "doc-20"),
                q("首次重大疾病保险金怎么赔", terms, "doc-01"),
                q("中症可以赔几次", terms, "doc-01"),
                q("轻症保险金比例是多少", terms, "doc-01"),
                q("住院津贴一年最多多少天", terms, "doc-01"),
                q("哪些情况保险公司不赔", terms, "doc-01", "doc-04"),
                q("酒驾导致事故属于免责吗", terms, "doc-01", "doc-04"),
                q("合同怎么申请解除", terms, "doc-01"),
                q("保险事故后多久要通知公司", terms, "doc-01"),
                q("健康告知问了哪些疾病", health, "doc-21"),
                q("过去两年住院需要告知吗", health, "doc-21"),
                q("BMI超过30是否在健康告知里", health, "doc-21"),
                q("健康告知页面能直接完成投保吗", health, "doc-21")
        );
    }

    private QueryCase q(String query, Set<DocumentType> documentTypes, String... expectedDocumentIds) {
        return new QueryCase(query, documentTypes, Set.of(expectedDocumentIds));
    }

    private record QueryCase(
            String query,
            Set<DocumentType> documentTypes,
            Set<String> expectedDocumentIds
    ) {
    }
}

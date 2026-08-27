package com.embabel.insurance.xiaoming.occupation;

import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersion;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionStatus;
import com.embabel.insurance.xiaoming.knowledge.importer.ImportedKnowledgeBundle;
import com.embabel.insurance.xiaoming.knowledge.importer.KnowledgeSnapshotStore;
import com.embabel.insurance.xiaoming.knowledge.importer.OccupationEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OccupationServiceTest {

    private KnowledgeSnapshotStore store;
    private OccupationService service;

    @BeforeEach
    void setUp() {
        store = publishedStore("knowledge-v1", "occupation-v1", occupations());
        service = new OccupationService(store);
    }

    @Test
    void normalizesFullWidthAndSafeAliasesWithoutReturningCategoryBeforeConfirmation() {
        OccupationSearchResult result = service.search(
                "36743",
                "软件工程师",
                null,
                "query-token-0001"
        );

        assertFalse(result.requiresMoreDetail());
        assertEquals(1, result.candidates().size());
        assertEquals("计算机软件工程技术人员", result.candidates().getFirst().name());
        assertEquals("10000002", result.candidates().getFirst().occupationId());
        assertEquals("occupation-v1", result.occupationVersion());

        OccupationSearchResult fullWidth = service.search(
                "36743",
                "程序员Ａ",
                null,
                "query-token-0002"
        );
        assertTrue(fullWidth.candidates().size() <= OccupationService.MAX_CANDIDATES);
    }

    @Test
    void returnsAtMostFiveCandidatesForAmbiguousDescription() {
        OccupationSearchResult result = service.search("36743", "司机", null, "query-token-0003");

        assertFalse(result.requiresMoreDetail());
        assertEquals(5, result.candidates().size());
        assertTrue(result.candidates().stream().allMatch(candidate -> candidate.name().contains("司机")));
    }

    @Test
    void asksForMoreDetailForVagueUnknownAndMultipleOccupations() {
        assertTrue(service.search("36743", "工作", null, "query-token-0004").requiresMoreDetail());
        assertTrue(service.search("36743", "量子占卜师", null, "query-token-0005").requiresMoreDetail());

        OccupationSearchResult multiple = service.search(
                "36743",
                "司机和厨师",
                null,
                "query-token-0006"
        );
        assertTrue(multiple.requiresMoreDetail());
        assertTrue(multiple.prompt().contains("分别查询"));
    }

    @Test
    void confirmsOnlyByExactIdAndReturnsVersionPathAndBoundary() {
        OccupationConfirmationResult matched = service.confirm("36743", "10000001", "knowledge-v1");

        assertTrue(matched.matched());
        assertEquals(1, matched.category());
        assertEquals("occupation-v1", matched.occupationVersion());
        assertEquals(List.of("互联网业", "软件服务", "程序设计", "计算机程序设计员"), matched.path());
        assertTrue(matched.notice().contains("不代表保险公司最终承保结论"));

        OccupationConfirmationResult missing = service.confirm("36743", "99999999", "knowledge-v1");
        assertFalse(missing.matched());
        assertNull(missing.category());
        assertTrue(missing.path().isEmpty());
    }

    @Test
    void stopsConfirmationWhenPublishedVersionChanged() {
        store.replace(
                version("knowledge-v2", "occupation-v2"),
                bundle("knowledge-v2", "occupation-v2", occupations())
        );

        OccupationServiceException exception = assertThrows(
                OccupationServiceException.class,
                () -> service.confirm("36743", "10000001", "knowledge-v1")
        );
        assertEquals(OccupationServiceException.Reason.VERSION_CONFLICT, exception.reason());
        assertEquals("knowledge-v2", exception.currentKnowledgeVersion());
    }

    @Test
    void failsClosedWithoutPublishedOccupationSnapshot() {
        OccupationService unavailable = new OccupationService(new KnowledgeSnapshotStore());
        OccupationServiceException exception = assertThrows(
                OccupationServiceException.class,
                () -> unavailable.search("36743", "程序员", null, "query-token-0007")
        );
        assertEquals(OccupationServiceException.Reason.UNAVAILABLE, exception.reason());
        assertThrows(IllegalArgumentException.class,
                () -> service.search("other", "程序员", null, "query-token-0008"));
    }

    @Test
    void representativeSearchP95IsBelowTwoSeconds() {
        List<Long> samples = new ArrayList<>();
        List<String> queries = List.of("程序员", "软件工程师", "教师", "司机", "厨师", "快递员");
        for (int index = 0; index < 240; index++) {
            long started = System.nanoTime();
            service.search("36743", queries.get(index % queries.size()), null, "query-token-perf-" + index);
            samples.add(System.nanoTime() - started);
        }
        Collections.sort(samples);
        long p95Nanos = samples.get((int) Math.ceil(samples.size() * 0.95d) - 1);
        assertTrue(p95Nanos < 2_000_000_000L, "occupation search P95 must be below two seconds");
    }

    private KnowledgeSnapshotStore publishedStore(
            String knowledgeVersion,
            String occupationVersion,
            List<OccupationEntry> occupations
    ) {
        KnowledgeSnapshotStore result = new KnowledgeSnapshotStore();
        result.replace(
                version(knowledgeVersion, occupationVersion),
                bundle(knowledgeVersion, occupationVersion, occupations)
        );
        return result;
    }

    private ImportedKnowledgeBundle bundle(
            String knowledgeVersion,
            String occupationVersion,
            List<OccupationEntry> occupations
    ) {
        return new ImportedKnowledgeBundle(
                version(knowledgeVersion, occupationVersion),
                List.of(),
                List.of(),
                occupations,
                List.of()
        );
    }

    private KnowledgeVersion version(String knowledgeVersion, String occupationVersion) {
        return new KnowledgeVersion(
                knowledgeVersion,
                "36743",
                KnowledgeVersionStatus.PUBLISHED,
                "rules-v1",
                occupationVersion,
                Instant.parse("2026-08-28T00:00:00Z"),
                List.of()
        );
    }

    private List<OccupationEntry> occupations() {
        return List.of(
                occupation("10000001", "互联网业", "软件服务", "程序设计", "计算机程序设计员", 1),
                occupation("10000002", "互联网业", "信息工程", "软件工程", "计算机软件工程技术人员", 1),
                occupation("10000003", "教育业", "教育机构", "教师", "一般教师", 1),
                occupation("10000004", "交通运输业", "陆运业", "驾驶人员", "出租车司机", 4),
                occupation("10000005", "交通运输业", "陆运业", "驾驶人员", "公务小客车司机", 2),
                occupation("10000006", "交通运输业", "陆运业", "驾驶人员", "救护车司机", 4),
                occupation("10000007", "交通运输业", "陆运业", "驾驶人员", "校车司机", 3),
                occupation("10000008", "交通运输业", "陆运业", "驾驶人员", "货柜车司机", 4),
                occupation("10000009", "餐饮业", "烹饪人员", "中餐烹饪", "中餐厨师", 2),
                occupation("10000010", "服务业", "快递业", "物流服务", "物流、快递人员", 3)
        );
    }

    private OccupationEntry occupation(
            String id,
            String big,
            String second,
            String minor,
            String fourth,
            int category
    ) {
        return new OccupationEntry(id, "occupation-v1", big, second, minor, fourth, category, false);
    }
}

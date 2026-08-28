package com.embabel.insurance.xiaoming.health;

import com.embabel.insurance.xiaoming.api.HealthFactRequest;
import com.embabel.insurance.xiaoming.api.HealthMatchRequest;
import com.embabel.insurance.xiaoming.config.XiaomingHealthProperties;
import com.embabel.insurance.xiaoming.knowledge.ClauseChunk;
import com.embabel.insurance.xiaoming.knowledge.DocumentType;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersion;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionStatus;
import com.embabel.insurance.xiaoming.knowledge.importer.ImportedKnowledgeBundle;
import com.embabel.insurance.xiaoming.knowledge.importer.KnowledgeSnapshotStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthMatchServiceTest {

    private HealthMatchService service;

    @BeforeEach
    void setUp() {
        KnowledgeSnapshotStore store = publishedStore();
        service = new HealthMatchService(
                store,
                new XiaomingHealthProperties(),
                Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void diseaseProducesHitWithoutInventingUnderwritingConclusion() {
        HealthMatchResult result = service.match(request(fact("HYPERTENSION", "DISEASE", "高血压", null,
                "PRESENT", null, null)));

        assertEquals(HealthMatchState.HIT, result.state());
        assertEquals("2", result.questions().getFirst().questionId());
        assertTrue(result.notice().contains("不是核保结论"));
        assertTrue(result.questions().getFirst().evidence().documentName().contains("健康告知"));
    }

    @Test
    void confirmedNegativeFactsProduceQualifiedNoHitFound() {
        HealthMatchResult result = service.match(request(
                fact("HYPERTENSION", "DISEASE", "否认高血压", null, "ABSENT", null, null),
                fact("HOSPITALIZATION", "HOSPITALIZATION", "无住院史", null, "ABSENT", null, null)
        ));

        assertEquals(HealthMatchState.NO_HIT_FOUND, result.state());
        assertTrue(result.notice().contains("仅在当前确认资料中暂未发现命中"));
    }

    @Test
    void missingDateProducesInsufficientInsteadOfGuessingTimeWindow() {
        HealthMatchResult result = service.match(request(
                fact("HOSPITALIZATION", "HOSPITALIZATION", "曾住院", null, "PRESENT", null, null)
        ));

        assertEquals(HealthMatchState.INSUFFICIENT, result.state());
        assertTrue(result.questions().getFirst().missingFields().contains("发生日期"));
    }

    @Test
    void recentAndOldHospitalizationRespectTwoYearBoundary() {
        HealthMatchResult recent = service.match(request(
                fact("HOSPITALIZATION", "HOSPITALIZATION", "住院", LocalDate.of(2024, 8, 28),
                        "PRESENT", null, null)
        ));
        HealthMatchResult old = service.match(request(
                fact("HOSPITALIZATION", "HOSPITALIZATION", "住院", LocalDate.of(2024, 8, 27),
                        "PRESENT", null, null)
        ));

        assertEquals(HealthMatchState.HIT, recent.state());
        assertEquals(HealthMatchState.NO_HIT_FOUND, old.state());
    }

    @Test
    void bmiOverThirtyHitsAndMissingUnitIsInsufficient() {
        HealthMatchResult hit = service.match(request(
                fact("BMI", "MEASUREMENT", "BMI 31", null, "PRESENT", 31.0, "kg/m²")
        ));
        HealthMatchResult insufficient = service.match(request(
                fact("BMI", "MEASUREMENT", "BMI 31", null, "PRESENT", 31.0, null)
        ));

        assertEquals(HealthMatchState.HIT, hit.state());
        assertEquals(HealthMatchState.INSUFFICIENT, insufficient.state());
        assertTrue(insufficient.questions().getFirst().missingFields().contains("BMI 单位"));
    }

    @Test
    void childAndFemaleRulesRequireApplicableDemographics() {
        HealthMatchRequest child = request(fact("PREMATURE_BIRTH", "BIRTH_HISTORY", "早产", null,
                "PRESENT", null, null));
        child.setInsuredAgeYears(2);
        HealthMatchRequest missingFemaleContext = request(fact("FEMALE_CONDITION", "PREGNANCY", "卵巢囊肿", null,
                "PRESENT", null, null));

        assertEquals(HealthMatchState.HIT, service.match(child).state());
        assertEquals(HealthMatchState.INSUFFICIENT, service.match(missingFemaleContext).state());
    }

    @Test
    void conflictingFactsAreInsufficientAndDoNotSelfTriggerAHit() {
        HealthMatchResult result = service.match(request(
                fact("HYPERTENSION", "DISEASE", "患高血压", null, "PRESENT", null, null),
                fact("HYPERTENSION", "DISEASE", "否认高血压", null, "ABSENT", null, null)
        ));

        assertEquals(HealthMatchState.INSUFFICIENT, result.state());
        assertTrue(result.questions().stream().anyMatch(item -> item.state() == HealthMatchState.INSUFFICIENT));
    }

    @Test
    void unconfirmedFactsAndSensitiveValuesAreRejected() {
        HealthMatchRequest unconfirmed = request(fact("HYPERTENSION", "DISEASE", "高血压", null,
                "PRESENT", null, null));
        unconfirmed.getFacts().getFirst().setConfirmed(false);
        HealthMatchRequest sensitive = request(fact("HYPERTENSION", "DISEASE", "高血压 13812345678", null,
                "PRESENT", null, null));

        assertThrows(HealthServiceException.class, () -> service.match(unconfirmed));
        assertThrows(HealthServiceException.class, () -> service.match(sensitive));
    }

    private HealthMatchRequest request(HealthFactRequest... facts) {
        HealthMatchRequest request = new HealthMatchRequest();
        request.setProductId("36743");
        request.setConversationId("conversation-health-0001");
        request.setKnowledgeVersion("knowledge-health-v1");
        request.setConfirmed(true);
        request.setInsuredSex("UNSPECIFIED");
        request.setFacts(List.of(facts));
        return request;
    }

    private HealthFactRequest fact(
            String code,
            String type,
            String value,
            LocalDate date,
            String polarity,
            Double numeric,
            String unit
    ) {
        HealthFactRequest fact = new HealthFactRequest();
        fact.setFactId("health-fact-" + code + '-' + Math.abs(value.hashCode()));
        fact.setCode(code);
        fact.setType(type);
        fact.setLabel(code);
        fact.setValue(value);
        fact.setDate(date);
        fact.setPolarity(polarity);
        fact.setNumericValue(numeric);
        fact.setUnit(unit);
        fact.setConfidence(0.95);
        fact.setConfirmed(true);
        HealthFactRequest.HealthSourceLocationRequest source = new HealthFactRequest.HealthSourceLocationRequest();
        source.setSourceId("manual-text");
        source.setSourceLabel("手工文字");
        source.setLine(1);
        source.setExcerpt(value);
        fact.setSourceLocation(source);
        return fact;
    }

    private KnowledgeSnapshotStore publishedStore() {
        KnowledgeSnapshotStore store = new KnowledgeSnapshotStore();
        KnowledgeVersion version = new KnowledgeVersion(
                "knowledge-health-v1", "36743", KnowledgeVersionStatus.PUBLISHED,
                "rules-health-v1", "occupation-v1", Instant.parse("2026-08-28T00:00:00Z"), List.of()
        );
        String text = "健康告知仅供预览 就医行为及保险情况 目前或曾经患有 最近1年内是否存在过 "
                + "以下一项或几项检查异常 两周岁（含）以下儿童告知 16周岁（含）以上女性告知 职业情况";
        ClauseChunk chunk = new ClauseChunk(
                "health-evidence-0001", "36743", "knowledge-health-v1", "doc-21",
                "21-健康告知预览.txt", DocumentType.HEALTH_DISCLOSURE, "健康告知", 1, null, text, Set.of("健康")
        );
        store.replace(version, new ImportedKnowledgeBundle(version, List.of(chunk), List.of(), List.of(), List.of()));
        return store;
    }
}

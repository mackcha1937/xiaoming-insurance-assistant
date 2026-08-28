package com.embabel.insurance.xiaoming.health;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthFactExtractorTest {

    private final HealthFactExtractor extractor = new HealthFactExtractor(
            Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void extractsEventsDatesDiseasesAndSourceLocations() {
        List<HealthFactDraft> facts = extract("2025年3月因高血压住院并接受手术，目前服药并建议复查。");

        assertTrue(facts.stream().anyMatch(fact -> fact.code().equals("HYPERTENSION")));
        assertTrue(facts.stream().anyMatch(fact -> fact.code().equals("HOSPITALIZATION")));
        assertTrue(facts.stream().anyMatch(fact -> fact.code().equals("SURGERY")));
        assertTrue(facts.stream().anyMatch(fact -> fact.code().equals("MEDICATION")));
        assertTrue(facts.stream().anyMatch(fact -> fact.code().equals("FOLLOW_UP")));
        assertTrue(facts.stream().allMatch(fact -> fact.sourceLocation().line() == 1));
        assertEquals(2025, facts.getFirst().date().getYear());
        assertTrue(facts.stream().noneMatch(HealthFactDraft::confirmed));
    }

    @Test
    void preservesNegativeSemanticsWithoutTurningThemIntoPositiveFacts() {
        List<HealthFactDraft> facts = extract("否认高血压、糖尿病，无住院或手术史。");

        assertEquals(4, facts.size());
        assertTrue(facts.stream().allMatch(fact -> fact.polarity() == HealthFactPolarity.ABSENT));
    }

    @Test
    void computesBmiAndConvertsWeightLossUnits() {
        List<HealthFactDraft> facts = extract("目前身高170厘米，体重90公斤。去年体重减轻12斤。");

        HealthFactDraft bmi = facts.stream().filter(fact -> fact.code().equals("BMI")).findFirst().orElseThrow();
        HealthFactDraft loss = facts.stream().filter(fact -> fact.code().equals("WEIGHT_LOSS")).findFirst().orElseThrow();
        assertEquals(31.1, bmi.numericValue());
        assertEquals("kg/m²", bmi.unit());
        assertEquals(6.0, loss.numericValue());
        assertEquals(2025, loss.date().getYear());
    }

    @Test
    void redactsIdentityPatternsFromDraftsAndExcerpts() {
        List<HealthFactDraft> facts = extract("张女士 13812345678，身份证110101199001011234，患高血压。");

        HealthFactDraft fact = facts.stream().filter(item -> item.code().equals("HYPERTENSION")).findFirst().orElseThrow();
        assertFalse(fact.value().contains("13812345678"));
        assertFalse(fact.value().contains("110101199001011234"));
        assertFalse(fact.sourceLocation().excerpt().contains("张女士"));
    }

    @Test
    void leavesDateEmptyInsteadOfGuessing() {
        HealthFactDraft fact = extract("曾因胸痛就诊。").getFirst();
        assertNull(fact.date());
    }

    private List<HealthFactDraft> extract(String text) {
        return extractor.extract(List.of(new HealthFactExtractor.SourceText(
                "source-1", "手工文字", null, text, 0.95
        )));
    }
}

package com.embabel.insurance.xiaoming.conversation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConversationFactExtractorTest {

    private final ConversationFactExtractor extractor = new ConversationFactExtractor();

    @Test
    void shouldExtractFactsWithoutOverwritingKnownContext() {
        Map<String, Object> facts = extractor.extract(
                "被保险人30岁，想20年交，保终身，等待期多久",
                Map.of("insuredAgeYears", 31, "unknown", "must-not-pass")
        );

        assertEquals(31, facts.get("insuredAgeYears"));
        assertEquals(20, facts.get("paymentYears"));
        assertEquals("LIFETIME", facts.get("coverageTerm"));
        assertEquals("WAITING_PERIOD", facts.get("ruleQuery"));
        assertFalse(facts.containsKey("unknown"));
    }

    @Test
    void shouldTreatApplicantAgeAndSinglePaymentSeparately() {
        Map<String, Object> facts = extractor.extract("投保人40岁，趸交，保至70岁", Map.of());

        assertEquals(40, facts.get("applicantAgeYears"));
        assertEquals(0, facts.get("paymentYears"));
        assertEquals("TO_70", facts.get("coverageTerm"));
        assertFalse(facts.containsKey("insuredAgeYears"));
    }
}

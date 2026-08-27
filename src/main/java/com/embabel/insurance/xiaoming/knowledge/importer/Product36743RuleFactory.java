package com.embabel.insurance.xiaoming.knowledge.importer;

import com.embabel.insurance.xiaoming.knowledge.ClauseChunk;
import com.embabel.insurance.xiaoming.rules.BusinessRule;
import com.embabel.insurance.xiaoming.rules.ComparisonOperator;
import com.embabel.insurance.xiaoming.rules.ConditionNode;
import com.embabel.insurance.xiaoming.rules.GroupCondition;
import com.embabel.insurance.xiaoming.rules.LogicOperator;
import com.embabel.insurance.xiaoming.rules.PredicateCondition;

import java.util.ArrayList;
import java.util.List;

public class Product36743RuleFactory {

    public List<BusinessRule> create(List<ClauseChunk> chunks) {
        List<BusinessRule> rules = new ArrayList<>();
        String applicantEvidence = evidence(chunks, "doc-06", 1, "投保人应为18周岁");
        rules.add(rule("applicant-age", "APPLICANT_AGE_ELIGIBLE", 120,
                and(gte("applicantAgeYears", 18), lt("applicantAgeYears", 55)), applicantEvidence));

        String occupationEvidence = evidence(chunks, "doc-06", 1, "职业类别应为1-4类");
        rules.add(rule("occupation-class-allowed", "OCCUPATION_CLASS_ALLOWED", 120,
                in("occupationClass", List.of(1, 2, 3, 4)), occupationEvidence));
        rules.add(rule("occupation-class-disallowed", "OCCUPATION_CLASS_NOT_ALLOWED", 120,
                in("occupationClass", List.of(0, 5, 6)), occupationEvidence));

        String ageEvidence = evidence(chunks, "doc-12", 1, "不同缴费期限和保障期限");
        rules.add(ageLimit("insured-age-lifetime-single", "LIFETIME", 0, 55, ageEvidence));
        rules.add(ageLimit("insured-age-lifetime-5", "LIFETIME", 5, 55, ageEvidence));
        rules.add(ageLimit("insured-age-lifetime-10", "LIFETIME", 10, 50, ageEvidence));
        rules.add(ageLimit("insured-age-lifetime-20", "LIFETIME", 20, 45, ageEvidence));
        rules.add(ageLimit("insured-age-lifetime-30", "LIFETIME", 30, 35, ageEvidence));
        rules.add(ageLimit("insured-age-lifetime-35", "LIFETIME", 35, 30, ageEvidence));
        rules.add(rule("insured-age-to-70", "MAX_INSURED_AGE_YEARS=35", 115,
                and(eq("coverageTerm", "TO_70"), lte("insuredAgeYears", 35)), ageEvidence));
        rules.add(rule("disease-care-age-limit", "MAX_INSURED_AGE_YEARS=35", 116,
                and(eq("coverageTerm", "LIFETIME"), eq("diseaseCareSelected", true),
                        lte("insuredAgeYears", 35)), ageEvidence));

        String amountEvidence = evidence(chunks, "doc-11", 1, "累计重疾基本保额");
        rules.add(amountLimit("age-0-40", 0, 40, 500_000, 1_750_000, amountEvidence));
        rules.add(amountLimit("age-41-45", 41, 45, 300_000, 1_050_000, amountEvidence));
        rules.add(amountLimit("age-46-50", 46, 50, 200_000, 700_000, amountEvidence));
        rules.add(amountLimit("age-51-55", 51, 55, 100_000, 150_000, amountEvidence));

        String responsibilityEvidence = evidence(chunks, "doc-13", 1, "责任终身保70岁");
        rules.add(rule("to-70-limited-responsibilities",
                "TO_70_OPTIONAL_RESPONSIBILITIES=DEATH_OR_TOTAL_DISABILITY,PREMIUM_COMPENSATION,PILLAR_CARE",
                110,
                eq("coverageTerm", "TO_70"), responsibilityEvidence));

        String waitingEvidence = evidence(chunks, "doc-07", 3, "180日为等待期");
        rules.add(rule("waiting-period", "WAITING_PERIOD_DAYS=180", 100,
                eq("ruleQuery", "WAITING_PERIOD"), waitingEvidence));
        String coolingEvidence = evidence(chunks, "doc-07", 2, "15日的犹豫期");
        rules.add(rule("cooling-off-period", "COOLING_OFF_DAYS=15", 100,
                eq("ruleQuery", "COOLING_OFF_PERIOD"), coolingEvidence));
        String claimsEvidence = evidence(chunks, "doc-07", 5, "索赔程序");
        rules.add(rule("claims-path", "CLAIMS_STEPS=REPORT,SUBMIT,REVIEW,PAY", 90,
                eq("ruleQuery", "CLAIMS_PATH"), claimsEvidence));
        return List.copyOf(rules);
    }

    private BusinessRule ageLimit(
            String id,
            String coverageTerm,
            int paymentYears,
            int maxAge,
            String evidenceId
    ) {
        return rule(id, "MAX_INSURED_AGE_YEARS=" + maxAge, 115,
                and(eq("coverageTerm", coverageTerm), eq("paymentYears", paymentYears),
                        lte("insuredAgeYears", maxAge)), evidenceId);
    }

    private BusinessRule amountLimit(
            String id,
            int minAge,
            int maxAge,
            int maxBasicAmount,
            int maxRiskAmount,
            String evidenceId
    ) {
        return rule(id,
                "MAX_BASIC_SUM_INSURED_CNY=" + maxBasicAmount + ";MAX_RISK_SUM_INSURED_CNY=" + maxRiskAmount,
                115,
                new PredicateCondition("insuredAgeYears", ComparisonOperator.BETWEEN, minAge, maxAge),
                evidenceId);
    }

    private BusinessRule rule(
            String id,
            String result,
            int priority,
            ConditionNode condition,
            String evidenceId
    ) {
        return new BusinessRule(
                id,
                Product36743SourceCatalog.PRODUCT_ID,
                Product36743SourceCatalog.KNOWLEDGE_VERSION,
                "APPLICATION_RULES",
                condition,
                result,
                priority,
                List.of(evidenceId)
        );
    }

    private String evidence(List<ClauseChunk> chunks, String documentId, int page, String phrase) {
        String target = compact(phrase);
        return chunks.stream()
                .filter(chunk -> chunk.documentId().equals(documentId))
                .filter(chunk -> chunk.pdfPage() == page)
                .filter(chunk -> compact(chunk.text()).contains(target))
                .map(ClauseChunk::chunkId)
                .findFirst()
                .orElseThrow(() -> new SourceImportException(
                        "rule evidence not found for " + documentId + " page " + page + ": " + phrase
                ));
    }

    private static String compact(String value) {
        return value.replaceAll("[\\s（）()—–－-]", "");
    }

    private static ConditionNode eq(String field, Object value) {
        return new PredicateCondition(field, ComparisonOperator.EQUALS, value, null);
    }

    private static ConditionNode in(String field, List<?> values) {
        return new PredicateCondition(field, ComparisonOperator.IN, values, null);
    }

    private static ConditionNode gte(String field, Number value) {
        return new PredicateCondition(field, ComparisonOperator.GREATER_THAN_OR_EQUAL, value, null);
    }

    private static ConditionNode lt(String field, Number value) {
        return new PredicateCondition(field, ComparisonOperator.LESS_THAN, value, null);
    }

    private static ConditionNode lte(String field, Number value) {
        return new PredicateCondition(field, ComparisonOperator.LESS_THAN_OR_EQUAL, value, null);
    }

    private static ConditionNode and(ConditionNode... children) {
        return new GroupCondition(LogicOperator.AND, List.of(children));
    }
}

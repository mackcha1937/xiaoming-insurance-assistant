package com.embabel.insurance.xiaoming.rules;

import com.embabel.insurance.xiaoming.knowledge.KnowledgeFixtures;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuleEngineTest {

    private final RuleEngine engine = new RuleEngine();

    @Test
    void evaluatesEqualsAndNormalizesNumericTypes() {
        RuleEvaluation evaluation = evaluate(
                predicate("age", ComparisonOperator.EQUALS, 18, null),
                Map.of("age", 18L)
        );

        assertEquals(RuleMatchStatus.MATCHED, evaluation.status());
    }

    @Test
    void evaluatesInclusiveNumberRanges() {
        ConditionNode range = predicate("age", ComparisonOperator.BETWEEN, 18, 55);

        assertEquals(RuleMatchStatus.MATCHED, evaluate(range, Map.of("age", 18)).status());
        assertEquals(RuleMatchStatus.MATCHED, evaluate(range, Map.of("age", 55)).status());
        assertEquals(RuleMatchStatus.NOT_MATCHED, evaluate(range, Map.of("age", 56)).status());
    }

    @Test
    void evaluatesOpenAndClosedNumericBounds() {
        assertEquals(RuleMatchStatus.NOT_MATCHED,
                evaluate(predicate("age", ComparisonOperator.LESS_THAN, 55, null), Map.of("age", 55)).status());
        assertEquals(RuleMatchStatus.MATCHED,
                evaluate(predicate("age", ComparisonOperator.LESS_THAN_OR_EQUAL, 55, null), Map.of("age", 55)).status());
        assertEquals(RuleMatchStatus.NOT_MATCHED,
                evaluate(predicate("age", ComparisonOperator.GREATER_THAN, 18, null), Map.of("age", 18)).status());
        assertEquals(RuleMatchStatus.MATCHED,
                evaluate(predicate("age", ComparisonOperator.GREATER_THAN_OR_EQUAL, 18, null), Map.of("age", 18)).status());
    }

    @Test
    void evaluatesCollectionsAndDateWindows() {
        assertEquals(
                RuleMatchStatus.MATCHED,
                evaluate(
                        predicate("region", ComparisonOperator.IN, List.of("上海", "北京"), null),
                        Map.of("region", "上海")
                ).status()
        );
        assertEquals(
                RuleMatchStatus.MATCHED,
                evaluate(
                        predicate(
                                "effectiveDate",
                                ComparisonOperator.DATE_BETWEEN,
                                "2026-01-01",
                                LocalDate.of(2026, 12, 31)
                        ),
                        Map.of("effectiveDate", LocalDate.of(2026, 8, 27))
                ).status()
        );
    }

    @Test
    void supportsExistsForPresentAndAbsentFacts() {
        assertEquals(
                RuleMatchStatus.MATCHED,
                evaluate(predicate("age", ComparisonOperator.EXISTS, true, null), Map.of("age", 30)).status()
        );
        assertEquals(
                RuleMatchStatus.MATCHED,
                evaluate(predicate("age", ComparisonOperator.EXISTS, false, null), Map.of()).status()
        );
    }

    @Test
    void reportsMinimalMissingFields() {
        RuleEvaluation evaluation = evaluate(
                new GroupCondition(
                        LogicOperator.AND,
                        List.of(
                                predicate("age", ComparisonOperator.BETWEEN, 18, 55),
                                predicate("region", ComparisonOperator.EQUALS, "上海", null)
                        )
                ),
                Map.of()
        );

        assertEquals(RuleMatchStatus.MISSING_INPUT, evaluation.status());
        assertEquals(Set.of("age", "region"), evaluation.missingFields());
    }

    @Test
    void shortCircuitsAndWhenAChildIsDefinitelyFalse() {
        RuleEvaluation evaluation = evaluate(
                new GroupCondition(
                        LogicOperator.AND,
                        List.of(
                                predicate("age", ComparisonOperator.BETWEEN, 18, 55),
                                predicate("region", ComparisonOperator.EQUALS, "上海", null)
                        )
                ),
                Map.of("age", 60)
        );

        assertEquals(RuleMatchStatus.NOT_MATCHED, evaluation.status());
        assertEquals(Set.of(), evaluation.missingFields());
    }

    @Test
    void supportsOrAndNotWithThreeStateLogic() {
        ConditionNode or = new GroupCondition(
                LogicOperator.OR,
                List.of(
                        predicate("region", ComparisonOperator.EQUALS, "上海", null),
                        predicate("age", ComparisonOperator.EQUALS, 30, null)
                )
        );
        assertEquals(RuleMatchStatus.MATCHED, evaluate(or, Map.of("age", 30)).status());
        assertEquals(
                RuleMatchStatus.MATCHED,
                evaluate(new GroupCondition(LogicOperator.NOT, List.of(or)), Map.of("age", 20, "region", "北京")).status()
        );
    }

    @Test
    void isolatesProductAndVersionAndOrdersByPriority() {
        BusinessRule low = rule("low", KnowledgeFixtures.PRODUCT_ID, KnowledgeFixtures.VERSION, 1);
        BusinessRule high = rule("high", KnowledgeFixtures.PRODUCT_ID, KnowledgeFixtures.VERSION, 10);
        BusinessRule old = rule("old", KnowledgeFixtures.PRODUCT_ID, "old", 100);
        BusinessRule other = rule("other", "other", KnowledgeFixtures.VERSION, 100);

        List<RuleEvaluation> result = engine.evaluate(
                KnowledgeFixtures.PRODUCT_ID,
                KnowledgeFixtures.VERSION,
                List.of(low, old, other, high),
                Map.of("age", 30)
        );

        assertEquals(List.of("high", "low"), result.stream()
                .map(evaluation -> evaluation.rule().ruleId())
                .toList());
    }

    @Test
    void rejectsMalformedConditionsAndWrongValueTypes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GroupCondition(LogicOperator.NOT, List.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluate(predicate("age", ComparisonOperator.BETWEEN, 18, 55), Map.of("age", "thirty"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluate(predicate("region", ComparisonOperator.IN, "上海", null), Map.of("region", "上海"))
        );
    }

    private RuleEvaluation evaluate(ConditionNode condition, Map<String, Object> facts) {
        BusinessRule rule = new BusinessRule(
                "rule",
                KnowledgeFixtures.PRODUCT_ID,
                KnowledgeFixtures.VERSION,
                "APPLICATION_RULES",
                condition,
                "ALLOW",
                1,
                List.of()
        );
        return engine.evaluate(
                KnowledgeFixtures.PRODUCT_ID,
                KnowledgeFixtures.VERSION,
                List.of(rule),
                facts
        ).getFirst();
    }

    private PredicateCondition predicate(
            String field,
            ComparisonOperator operator,
            Object expected,
            Object upper
    ) {
        return new PredicateCondition(field, operator, expected, upper);
    }

    private BusinessRule rule(String id, String productId, String version, int priority) {
        return new BusinessRule(
                id,
                productId,
                version,
                "APPLICATION_RULES",
                predicate("age", ComparisonOperator.BETWEEN, 18, 55),
                "ALLOW",
                priority,
                List.of()
        );
    }
}

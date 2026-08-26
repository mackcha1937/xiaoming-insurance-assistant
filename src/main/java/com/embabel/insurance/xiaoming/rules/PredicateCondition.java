package com.embabel.insurance.xiaoming.rules;

public record PredicateCondition(
        String field,
        ComparisonOperator operator,
        Object expected,
        Object upperBound
) implements ConditionNode {

    public PredicateCondition {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field must not be blank");
        }
        if (operator == null) {
            throw new IllegalArgumentException("operator must not be null");
        }
        if (operator != ComparisonOperator.EXISTS && expected == null) {
            throw new IllegalArgumentException("expected must be present for " + operator);
        }
        if ((operator == ComparisonOperator.BETWEEN || operator == ComparisonOperator.DATE_BETWEEN)
                && upperBound == null) {
            throw new IllegalArgumentException("upperBound is required for ranges");
        }
    }
}

package com.embabel.insurance.xiaoming.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class RuleEngine {

    public List<RuleEvaluation> evaluate(
            String productId,
            String version,
            List<BusinessRule> rules,
            Map<String, Object> facts
    ) {
        return rules.stream()
                .filter(rule -> rule.productId().equals(productId))
                .filter(rule -> rule.version().equals(version))
                .sorted(Comparator.comparingInt(BusinessRule::priority).reversed()
                        .thenComparing(BusinessRule::ruleId))
                .map(rule -> {
                    ConditionResult result = evaluateNode(rule.condition(), facts);
                    return new RuleEvaluation(rule, result.status(), result.missingFields());
                })
                .toList();
    }

    private ConditionResult evaluateNode(ConditionNode node, Map<String, Object> facts) {
        if (node instanceof PredicateCondition predicate) {
            return evaluatePredicate(predicate, facts);
        }
        GroupCondition group = (GroupCondition) node;
        List<ConditionResult> children = group.children().stream()
                .map(child -> evaluateNode(child, facts))
                .toList();
        return switch (group.operator()) {
            case AND -> and(children);
            case OR -> or(children);
            case NOT -> not(children.getFirst());
        };
    }

    private ConditionResult evaluatePredicate(PredicateCondition condition, Map<String, Object> facts) {
        boolean present = facts.containsKey(condition.field()) && facts.get(condition.field()) != null;
        if (condition.operator() == ComparisonOperator.EXISTS) {
            boolean expected = condition.expected() == null || Boolean.TRUE.equals(condition.expected());
            return matched(present == expected);
        }
        if (!present) {
            return new ConditionResult(
                    RuleMatchStatus.MISSING_INPUT,
                    Set.of(condition.field())
            );
        }
        Object actual = facts.get(condition.field());
        boolean matches = switch (condition.operator()) {
            case EQUALS -> equivalent(actual, condition.expected());
            case IN -> in(actual, condition.expected());
            case BETWEEN -> numberBetween(actual, condition.expected(), condition.upperBound());
            case DATE_BETWEEN -> dateBetween(actual, condition.expected(), condition.upperBound());
            case GREATER_THAN -> compareNumbers(actual, condition.expected()) > 0;
            case GREATER_THAN_OR_EQUAL -> compareNumbers(actual, condition.expected()) >= 0;
            case LESS_THAN -> compareNumbers(actual, condition.expected()) < 0;
            case LESS_THAN_OR_EQUAL -> compareNumbers(actual, condition.expected()) <= 0;
            case EXISTS -> throw new IllegalStateException("EXISTS handled before switch");
        };
        return matched(matches);
    }

    private ConditionResult and(List<ConditionResult> children) {
        if (children.stream().anyMatch(result -> result.status() == RuleMatchStatus.NOT_MATCHED)) {
            return matched(false);
        }
        Set<String> missing = mergeMissing(children);
        return missing.isEmpty()
                ? matched(true)
                : new ConditionResult(RuleMatchStatus.MISSING_INPUT, missing);
    }

    private ConditionResult or(List<ConditionResult> children) {
        if (children.stream().anyMatch(result -> result.status() == RuleMatchStatus.MATCHED)) {
            return matched(true);
        }
        Set<String> missing = mergeMissing(children);
        return missing.isEmpty()
                ? matched(false)
                : new ConditionResult(RuleMatchStatus.MISSING_INPUT, missing);
    }

    private ConditionResult not(ConditionResult child) {
        return switch (child.status()) {
            case MATCHED -> matched(false);
            case NOT_MATCHED -> matched(true);
            case MISSING_INPUT -> child;
        };
    }

    private Set<String> mergeMissing(List<ConditionResult> results) {
        Set<String> missing = new LinkedHashSet<>();
        results.forEach(result -> missing.addAll(result.missingFields()));
        return Set.copyOf(missing);
    }

    private boolean equivalent(Object actual, Object expected) {
        if (actual instanceof Number && expected instanceof Number) {
            return decimal(actual).compareTo(decimal(expected)) == 0;
        }
        return Objects.equals(actual, expected);
    }

    private boolean in(Object actual, Object expected) {
        if (!(expected instanceof Collection<?> values)) {
            throw new IllegalArgumentException("IN expected value must be a collection");
        }
        return values.stream().anyMatch(value -> equivalent(actual, value));
    }

    private boolean numberBetween(Object actual, Object lower, Object upper) {
        BigDecimal value = decimal(actual);
        return value.compareTo(decimal(lower)) >= 0 && value.compareTo(decimal(upper)) <= 0;
    }

    private int compareNumbers(Object actual, Object expected) {
        return decimal(actual).compareTo(decimal(expected));
    }

    private boolean dateBetween(Object actual, Object lower, Object upper) {
        LocalDate value = date(actual);
        return !value.isBefore(date(lower)) && !value.isAfter(date(upper));
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        throw new IllegalArgumentException("numeric rule requires numeric values");
    }

    private LocalDate date(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof String text) {
            return LocalDate.parse(text);
        }
        throw new IllegalArgumentException("date rule requires LocalDate or ISO date text");
    }

    private ConditionResult matched(boolean matches) {
        return new ConditionResult(
                matches ? RuleMatchStatus.MATCHED : RuleMatchStatus.NOT_MATCHED,
                Set.of()
        );
    }

    private record ConditionResult(RuleMatchStatus status, Set<String> missingFields) {
    }
}

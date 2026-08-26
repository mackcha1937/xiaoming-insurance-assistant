package com.embabel.insurance.xiaoming.rules;

import java.util.List;

public record GroupCondition(LogicOperator operator, List<ConditionNode> children)
        implements ConditionNode {

    public GroupCondition {
        if (operator == null) {
            throw new IllegalArgumentException("operator must not be null");
        }
        children = children == null ? List.of() : List.copyOf(children);
        if (operator == LogicOperator.NOT && children.size() != 1) {
            throw new IllegalArgumentException("NOT requires exactly one child");
        }
        if (operator != LogicOperator.NOT && children.isEmpty()) {
            throw new IllegalArgumentException(operator + " requires at least one child");
        }
    }
}

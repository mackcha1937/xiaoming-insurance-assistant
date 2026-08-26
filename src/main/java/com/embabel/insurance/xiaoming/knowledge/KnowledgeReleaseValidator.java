package com.embabel.insurance.xiaoming.knowledge;

import java.util.List;

@FunctionalInterface
public interface KnowledgeReleaseValidator {

    List<String> validate(KnowledgeVersion candidate);
}

package com.embabel.insurance.xiaoming.knowledge.importer;

import com.embabel.insurance.xiaoming.knowledge.ClauseChunk;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersion;
import com.embabel.insurance.xiaoming.rules.BusinessRule;

import java.util.List;

public record ImportedKnowledgeBundle(
        KnowledgeVersion knowledgeVersion,
        List<ClauseChunk> chunks,
        List<BusinessRule> rules,
        List<OccupationEntry> occupations,
        List<SpreadsheetSummary> spreadsheets
) {
    public ImportedKnowledgeBundle {
        chunks = List.copyOf(chunks);
        rules = List.copyOf(rules);
        occupations = List.copyOf(occupations);
        spreadsheets = List.copyOf(spreadsheets);
    }
}

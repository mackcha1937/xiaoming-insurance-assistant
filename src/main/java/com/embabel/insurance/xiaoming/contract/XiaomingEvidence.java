package com.embabel.insurance.xiaoming.contract;

public record XiaomingEvidence(
        String evidenceId,
        String documentName,
        String section,
        Integer pdfPage,
        Integer printedPage,
        String quote,
        String knowledgeVersion
) {
}

package com.embabel.insurance.xiaoming.checklist;

public class ChecklistServiceException extends RuntimeException {

    public enum Reason {
        SNAPSHOT_UNAVAILABLE,
        VERSION_CONFLICT
    }

    private final Reason reason;
    private final String currentKnowledgeVersion;

    public ChecklistServiceException(Reason reason, String message, String currentKnowledgeVersion) {
        super(message);
        this.reason = reason;
        this.currentKnowledgeVersion = currentKnowledgeVersion;
    }

    public Reason reason() {
        return reason;
    }

    public String currentKnowledgeVersion() {
        return currentKnowledgeVersion;
    }
}

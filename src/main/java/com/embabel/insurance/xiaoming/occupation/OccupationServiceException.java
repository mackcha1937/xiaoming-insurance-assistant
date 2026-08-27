package com.embabel.insurance.xiaoming.occupation;

public class OccupationServiceException extends RuntimeException {

    private final Reason reason;
    private final String currentKnowledgeVersion;

    public OccupationServiceException(Reason reason, String message, String currentKnowledgeVersion) {
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

    public enum Reason {
        UNAVAILABLE,
        VERSION_CONFLICT
    }
}

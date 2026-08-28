package com.embabel.insurance.xiaoming.health;

public class HealthServiceException extends RuntimeException {

    public enum Reason {
        VALIDATION,
        FILE_UNSUPPORTED,
        SNAPSHOT_UNAVAILABLE,
        VERSION_CONFLICT
    }

    private final Reason reason;
    private final String currentKnowledgeVersion;

    public HealthServiceException(Reason reason, String message, String currentKnowledgeVersion) {
        super(message);
        this.reason = reason;
        this.currentKnowledgeVersion = currentKnowledgeVersion == null ? "" : currentKnowledgeVersion;
    }

    public HealthServiceException(Reason reason, String message, String currentKnowledgeVersion, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.currentKnowledgeVersion = currentKnowledgeVersion == null ? "" : currentKnowledgeVersion;
    }

    public Reason reason() { return reason; }
    public String currentKnowledgeVersion() { return currentKnowledgeVersion; }
}

package com.embabel.insurance.xiaoming.knowledge;

public class KnowledgeReleaseException extends RuntimeException {

    public KnowledgeReleaseException(String message) {
        super(message);
    }

    public KnowledgeReleaseException(String message, Throwable cause) {
        super(message, cause);
    }
}

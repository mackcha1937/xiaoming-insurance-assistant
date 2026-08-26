package com.embabel.insurance.xiaoming.retrieval;

public class RetrievalDependencyException extends RuntimeException {

    public RetrievalDependencyException(String message) {
        super(message);
    }

    public RetrievalDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}

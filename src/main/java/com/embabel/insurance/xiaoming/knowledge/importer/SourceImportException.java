package com.embabel.insurance.xiaoming.knowledge.importer;

public class SourceImportException extends RuntimeException {

    public SourceImportException(String message) {
        super(message);
    }

    public SourceImportException(String message, Throwable cause) {
        super(message, cause);
    }
}

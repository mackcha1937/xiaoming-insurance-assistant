package com.embabel.insurance.xiaoming.application;

public class ApplicationLinkException extends RuntimeException {

    public enum Reason { NOT_CONFIGURED, INVALID_REQUEST }

    private final Reason reason;

    public ApplicationLinkException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}

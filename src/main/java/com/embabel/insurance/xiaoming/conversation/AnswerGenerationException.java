package com.embabel.insurance.xiaoming.conversation;

public class AnswerGenerationException extends RuntimeException {

    public AnswerGenerationException(String message) {
        super(message);
    }

    public AnswerGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

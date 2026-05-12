package com.web2health.oracle.exception;

public class AllSourcesFailedException extends RuntimeException {

    public AllSourcesFailedException(String message) {
        super(message);
    }

    public AllSourcesFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

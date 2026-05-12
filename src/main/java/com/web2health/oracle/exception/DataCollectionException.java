package com.web2health.oracle.exception;

public class DataCollectionException extends RuntimeException {

    private final String source;

    public DataCollectionException(String source, String message) {
        super(message);
        this.source = source;
    }

    public DataCollectionException(String source, String message, Throwable cause) {
        super(message, cause);
        this.source = source;
    }

    public String getSource() {
        return source;
    }
}

package com.company.exception;

public class ExternalEligibilityException extends RuntimeException {
    public ExternalEligibilityException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExternalEligibilityException(String message) {
        super(message);
    }
}

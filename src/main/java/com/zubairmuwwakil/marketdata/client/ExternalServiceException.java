package com.zubairmuwwakil.marketdata.client;

public class ExternalServiceException extends RuntimeException {

    private final boolean retryable;

    public ExternalServiceException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public ExternalServiceException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}

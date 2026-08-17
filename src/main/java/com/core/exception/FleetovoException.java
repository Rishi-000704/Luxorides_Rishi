package com.core.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public abstract class FleetovoException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	private final ErrorCode errorCode;
    private final HttpStatus status;
    private final Map<String, Object> metadata;

    protected FleetovoException(
            ErrorCode errorCode,
            HttpStatus status,
            String message,
            Map<String, Object> metadata
    ) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.metadata = metadata;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
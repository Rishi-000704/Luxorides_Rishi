package com.core.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class BusinessException extends FleetovoException {

	private static final long serialVersionUID = 1L;

	public BusinessException(ErrorCode code, String message) {
        super(code, HttpStatus.BAD_REQUEST, message, null);
    }

    public BusinessException(ErrorCode code, String message, Map<String, Object> metadata) {
        super(code, HttpStatus.BAD_REQUEST, message, metadata);
    }
}
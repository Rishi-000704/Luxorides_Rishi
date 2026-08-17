package com.core.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ValidationException extends FleetovoException {

	private static final long serialVersionUID = 1L;

	public ValidationException(Map<String, Object> fieldErrors) {
        super(
                ErrorCode.VALIDATION_ERROR,
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                Map.of("fields", fieldErrors)
        );
    }
}
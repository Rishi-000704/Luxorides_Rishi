package com.core.exception;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(

        String code,

        String message,

        int status,

        String path,

        String method,

        Instant timestamp,

        String traceId,

        String technicalMessage,

        String exceptionType,

        Map<String, Object> metadata

) {
}
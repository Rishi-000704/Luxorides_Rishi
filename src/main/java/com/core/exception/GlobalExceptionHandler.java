package com.core.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.core.config.RequestTraceFilter;
import com.core.models.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final int MAX_TECHNICAL_MESSAGE_LENGTH =
            1000;

    /**
     * Never expose complete stack traces through an API response.
     *
     * When enabled, only a sanitized root exception message and its type
     * are returned. The complete stack trace remains in backend logs.
     */
    @Value("${fleetovo.errors.expose-technical-message:false}")
    private boolean exposeTechnicalMessage;

    /*
     * =========================================================
     * BUSINESS AND DOMAIN ERRORS
     * =========================================================
     */

    @ExceptionHandler(FleetovoException.class)
    public ResponseEntity<ApiError> handleFleetovoException(
            FleetovoException ex,
            HttpServletRequest request
    ) {
        RequestActor actor = resolveRequestActor();
        String traceId = resolveTraceId(request);

        log.warn(
                "Business exception traceId={} code={} status={} method={} path={} userId={} orgId={} message={} metadataKeys={}",
                traceId,
                ex.getErrorCode(),
                ex.getStatus().value(),
                request.getMethod(),
                request.getRequestURI(),
                actor.userId(),
                actor.orgId(),
                ex.getMessage(),
                ex.getMetadata() == null
                        ? null
                        : ex.getMetadata().keySet()
        );

        ApiError error = createApiError(
                ex.getErrorCode(),
                ex.getMessage(),
                ex.getStatus(),
                request,
                traceId,
                null,
                null,
                ex.getMetadata()
        );

        return ResponseEntity
                .status(ex.getStatus())
                .body(error);
    }

    /*
     * =========================================================
     * REQUEST VALIDATION
     * =========================================================
     */

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        return validationResponse(
                ex.getBindingResult(),
                request
        );
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBindException(
            BindException ex,
            HttpServletRequest request
    ) {
        return validationResponse(
                ex.getBindingResult(),
                request
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        Map<String, Object> fieldErrors =
                new LinkedHashMap<>();

        ex.getConstraintViolations().forEach(violation -> {
            String field =
                    violation.getPropertyPath() == null
                            ? "request"
                            : violation.getPropertyPath().toString();

            fieldErrors.put(
                    field,
                    violation.getMessage()
            );
        });

        String traceId = resolveTraceId(request);
        RequestActor actor = resolveRequestActor();

        log.info(
                "Constraint validation failed traceId={} method={} path={} userId={} orgId={} fields={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                actor.userId(),
                actor.orgId(),
                fieldErrors.keySet()
        );

        ApiError error = createApiError(
                ErrorCode.VALIDATION_ERROR,
                "Validation failed",
                HttpStatus.BAD_REQUEST,
                request,
                traceId,
                null,
                null,
                Map.of("fields", fieldErrors)
        );

        return ResponseEntity
                .badRequest()
                .body(error);
    }

    /*
     * =========================================================
     * MALFORMED REQUESTS
     * =========================================================
     */

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableRequest(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return badRequestResponse(
                "Invalid request payload.",
                ex,
                request,
                null
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        Map<String, Object> metadata =
                new HashMap<>();

        metadata.put(
                "parameter",
                ex.getName()
        );

        if (ex.getRequiredType() != null) {
            metadata.put(
                    "expectedType",
                    ex.getRequiredType().getSimpleName()
            );
        }

        return badRequestResponse(
                "Invalid value for parameter: " + ex.getName(),
                ex,
                request,
                metadata
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        return badRequestResponse(
                "Required request parameter is missing: "
                        + ex.getParameterName(),
                ex,
                request,
                Map.of(
                        "parameter",
                        ex.getParameterName()
                )
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingRequestPart(
            MissingServletRequestPartException ex,
            HttpServletRequest request
    ) {
        return badRequestResponse(
                "Required request part is missing: "
                        + ex.getRequestPartName(),
                ex,
                request,
                Map.of(
                        "requestPart",
                        ex.getRequestPartName()
                )
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request
    ) {
        return badRequestResponse(
                "Uploaded file or request is too large.",
                ex,
                request,
                null
        );
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiError> handleMultipartException(
            MultipartException ex,
            HttpServletRequest request
    ) {
        return badRequestResponse(
                "Invalid multipart request.",
                ex,
                request,
                null
        );
    }

    /*
     * =========================================================
     * AUTHENTICATION
     * =========================================================
     */

    @ExceptionHandler({
            AuthenticationException.class,
            AuthenticationCredentialsNotFoundException.class
    })
    public ResponseEntity<ApiError> handleAuthenticationException(
            Exception ex,
            HttpServletRequest request
    ) {
        String traceId = resolveTraceId(request);

        log.warn(
                "Authentication failure traceId={} method={} path={} message={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                safeLogMessage(ex.getMessage())
        );

        ApiError error = createApiError(
                ErrorCode.UNAUTHORIZED,
                "Authentication required",
                HttpStatus.UNAUTHORIZED,
                request,
                traceId,
                null,
                null,
                null
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(error);
    }

    /*
     * =========================================================
     * AUTHORIZATION
     * =========================================================
     */

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        String traceId = resolveTraceId(request);
        RequestActor actor = resolveRequestActor();

        log.warn(
                "Access denied traceId={} method={} path={} userId={} orgId={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                actor.userId(),
                actor.orgId()
        );

        ApiError error = createApiError(
                ErrorCode.ACCESS_DENIED,
                "Access denied",
                HttpStatus.FORBIDDEN,
                request,
                traceId,
                null,
                null,
                null
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(error);
    }

    /*
     * =========================================================
     * INTERNAL SERVER ERRORS
     * =========================================================
     */

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleInternalException(
            Exception ex,
            HttpServletRequest request
    ) {
        String traceId = resolveTraceId(request);
        RequestActor actor = resolveRequestActor();
        Throwable rootCause = resolveRootCause(ex);

        String exceptionType =
                rootCause.getClass().getName();

        String technicalMessage =
                exposeTechnicalMessage
                        ? buildTechnicalMessage(rootCause)
                        : null;

        log.error(
                "Unhandled system exception traceId={} method={} path={} userId={} orgId={} exceptionType={} rootMessage={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                actor.userId(),
                actor.orgId(),
                exceptionType,
                safeLogMessage(rootCause.getMessage()),
                ex
        );

        ApiError error = createApiError(
                ErrorCode.INTERNAL_ERROR,
                "Something went wrong. Please try again.",
                HttpStatus.INTERNAL_SERVER_ERROR,
                request,
                traceId,
                technicalMessage,
                exposeTechnicalMessage
                        ? exceptionType
                        : null,
                null
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }

    /*
     * =========================================================
     * RESPONSE HELPERS
     * =========================================================
     */

    private ResponseEntity<ApiError> validationResponse(
            BindingResult bindingResult,
            HttpServletRequest request
    ) {
        Map<String, Object> fieldErrors =
                extractFieldErrors(bindingResult);

        String traceId = resolveTraceId(request);
        RequestActor actor = resolveRequestActor();

        log.info(
                "Request validation failed traceId={} method={} path={} userId={} orgId={} fields={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                actor.userId(),
                actor.orgId(),
                fieldErrors.keySet()
        );

        ApiError error = createApiError(
                ErrorCode.VALIDATION_ERROR,
                "Validation failed",
                HttpStatus.BAD_REQUEST,
                request,
                traceId,
                null,
                null,
                Map.of("fields", fieldErrors)
        );

        return ResponseEntity
                .badRequest()
                .body(error);
    }

    private ResponseEntity<ApiError> badRequestResponse(
            String message,
            Exception ex,
            HttpServletRequest request,
            Map<String, Object> metadata
    ) {
        String traceId = resolveTraceId(request);
        RequestActor actor = resolveRequestActor();

        log.info(
                "Bad request traceId={} method={} path={} userId={} orgId={} exceptionType={} message={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                actor.userId(),
                actor.orgId(),
                ex.getClass().getSimpleName(),
                safeLogMessage(ex.getMessage())
        );

        ApiError error = createApiError(
                ErrorCode.BAD_REQUEST,
                message,
                HttpStatus.BAD_REQUEST,
                request,
                traceId,
                null,
                null,
                metadata
        );

        return ResponseEntity
                .badRequest()
                .body(error);
    }

    private ApiError createApiError(
            ErrorCode errorCode,
            String message,
            HttpStatus status,
            HttpServletRequest request,
            String traceId,
            String technicalMessage,
            String exceptionType,
            Map<String, Object> metadata
    ) {
        return new ApiError(
                errorCode.name(),
                message,
                status.value(),
                request.getRequestURI(),
                request.getMethod(),
                Instant.now(),
                traceId,
                technicalMessage,
                exceptionType,
                metadata
        );
    }

    private Map<String, Object> extractFieldErrors(
            BindingResult bindingResult
    ) {
        Map<String, Object> fieldErrors =
                new LinkedHashMap<>();

        for (FieldError fieldError
                : bindingResult.getFieldErrors()) {

            fieldErrors.put(
                    fieldError.getField(),
                    fieldError.getDefaultMessage()
            );
        }

        return fieldErrors;
    }

    /*
     * =========================================================
     * DIAGNOSTIC HELPERS
     * =========================================================
     */

    private String resolveTraceId(
            HttpServletRequest request
    ) {
        Object value =
                request.getAttribute(
                        RequestTraceFilter.TRACE_ID_ATTRIBUTE
                );

        if (value instanceof String traceId
                && !traceId.isBlank()) {

            return traceId;
        }

        return UUID.randomUUID().toString();
    }

    private Throwable resolveRootCause(Throwable throwable) {
        Throwable rootCause =
                NestedExceptionUtils.getMostSpecificCause(
                        throwable
                );

        return rootCause != null
                ? rootCause
                : throwable;
    }

    private String buildTechnicalMessage(
            Throwable throwable
    ) {
        String exceptionName =
                throwable.getClass().getSimpleName();

        String message =
                sanitizeTechnicalMessage(
                        throwable.getMessage()
                );

        String result = message == null
                || message.isBlank()
                ? exceptionName
                : exceptionName + ": " + message;

        if (result.length()
                > MAX_TECHNICAL_MESSAGE_LENGTH) {

            return result.substring(
                    0,
                    MAX_TECHNICAL_MESSAGE_LENGTH
            );
        }

        return result;
    }

    private String sanitizeTechnicalMessage(
            String message
    ) {
        if (message == null || message.isBlank()) {
            return null;
        }

        return message
                .replaceAll(
                        "(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+",
                        "Bearer [redacted]"
                )
                .replaceAll(
                        "\\beyJ[A-Za-z0-9._-]+",
                        "[redacted-token]"
                )
                .replaceAll(
                        "(?i)(password|secret|authorization|api[-_ ]?key)"
                                + "\\s*([=:])\\s*([^,;\\s]+)",
                        "$1$2[redacted]"
                )
                .replaceAll(
                        "[\\r\\n\\t]+",
                        " "
                )
                .replaceAll(
                        "\\s{2,}",
                        " "
                )
                .trim();
    }

    private String safeLogMessage(String message) {
        String safe =
                sanitizeTechnicalMessage(message);

        if (safe == null) {
            return null;
        }

        return safe.length() > MAX_TECHNICAL_MESSAGE_LENGTH
                ? safe.substring(
                0,
                MAX_TECHNICAL_MESSAGE_LENGTH
        )
                : safe;
    }

    private RequestActor resolveRequestActor() {
        try {
            Authentication authentication =
                    SecurityContextHolder
                            .getContext()
                            .getAuthentication();

            if (authentication == null
                    || !authentication.isAuthenticated()) {

                return RequestActor.anonymous();
            }

            Object principal =
                    authentication.getPrincipal();

            if (principal instanceof User user) {
                return new RequestActor(
                        user.getId(),
                        user.getOrgId()
                );
            }

            return new RequestActor(
                    authentication.getName(),
                    null
            );

        } catch (Exception ignored) {
            return RequestActor.anonymous();
        }
    }

    private record RequestActor(
            String userId,
            String orgId
    ) {
        private static RequestActor anonymous() {
            return new RequestActor(
                    null,
                    null
            );
        }
    }
}
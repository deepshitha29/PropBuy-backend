package com.propbuy.exception;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException e, HttpServletRequest request) {
        return body(e.getStatus(), e.getCode(), e.getMessage(), null, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e, HttpServletRequest request) {
        var fields = new LinkedHashMap<String, String>();
        e.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation failed.", fields, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> constraints(HttpServletRequest request) {
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation failed.", null, request);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ErrorResponse> missing(HttpServletRequest request) {
        return body(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.", null, request);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> invalidState(IllegalStateException e, HttpServletRequest request) {
        return body(HttpStatus.BAD_REQUEST, "INVALID_STATE", e.getMessage(), null, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalidArgument(IllegalArgumentException e, HttpServletRequest request) {
        return body(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", e.getMessage(), null, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> denied(HttpServletRequest request) {
        return body(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action.", null, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> conflict(HttpServletRequest request) {
        return body(HttpStatus.CONFLICT, "DATA_CONFLICT", "This action conflicts with existing data.", null, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled API exception", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong on our side.", null, request);
    }

    private ResponseEntity<ErrorResponse> body(HttpStatus status, String code, String message,
                                               Map<String, String> fields, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), code, message,
                request.getRequestURI(), fields));
    }
}

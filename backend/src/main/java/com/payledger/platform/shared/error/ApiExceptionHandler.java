package com.payledger.platform.shared.error;

import com.payledger.platform.shared.web.TraceIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ResourceNotFoundException exception
    ) {
        return error(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                exception.getMessage()
        );
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(
            ConflictException exception
    ) {
        return error(
                HttpStatus.CONFLICT,
                "RESOURCE_CONFLICT",
                exception.getMessage()
        );
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotencyConflict(
            IdempotencyConflictException exception
    ) {
        return error(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_KEY_CONFLICT",
                exception.getMessage()
        );
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> handleInsufficientFunds(
            InsufficientFundsException exception
    ) {
        return error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INSUFFICIENT_FUNDS",
                exception.getMessage()
        );
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ApiError> handleBusinessRuleViolation(
            BusinessRuleViolationException exception
    ) {
        return error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                exception.getCode(),
                exception.getMessage()
        );
    }

    @ExceptionHandler(IdentityNotLinkedException.class)
    public ResponseEntity<ApiError> handleIdentityNotLinked(
            IdentityNotLinkedException exception
    ) {
        return error(
                HttpStatus.FORBIDDEN,
                "IDENTITY_NOT_LINKED",
                exception.getMessage()
        );
    }

    @ExceptionHandler(WalletAccessDeniedException.class)
    public ResponseEntity<ApiError> handleWalletAccessDenied(
            WalletAccessDeniedException exception
    ) {
        return error(
                HttpStatus.FORBIDDEN,
                "WALLET_ACCESS_DENIED",
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    public ResponseEntity<ApiError> handleInvalidWebhookSignature(
            InvalidWebhookSignatureException exception
    ) {
        return error(
                HttpStatus.UNAUTHORIZED,
                "INVALID_WEBHOOK_SIGNATURE",
                exception.getMessage()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleInvalidArgument(
            IllegalArgumentException exception
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT",
                exception.getMessage()
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(
            MissingRequestHeaderException exception
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "MISSING_REQUIRED_HEADER",
                "Required request header is missing: " + exception.getHeaderName()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return error(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                message
        );
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message
    ) {
        return ResponseEntity
                .status(status)
                .body(new ApiError(
                        code,
                        message,
                        MDC.get(TraceIdFilter.TRACE_ID),
                        Instant.now()
                ));
    }
}

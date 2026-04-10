package com.example.payments.api.error;

import com.example.payments.service.exception.IdempotencyConflictException;
import com.example.payments.service.exception.IdempotencyRequestInProgressException;
import com.example.payments.service.exception.InvalidPaymentStateException;
import com.example.payments.service.exception.MissingIdempotencyKeyException;
import com.example.payments.service.exception.PaymentNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .toList();

        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, details);
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MissingIdempotencyKeyException.class})
    public ResponseEntity<ApiError> handleMissingIdempotency(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "MISSING_IDEMPOTENCY_KEY",
                "Idempotency-Key header is required",
                request,
                List.of(ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotencyConflict(IdempotencyConflictException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
                "IDEMPOTENCY_KEY_CONFLICT",
            "Idempotency key conflict",
                request,
                List.of(ex.getMessage()));
    }

        @ExceptionHandler(IdempotencyRequestInProgressException.class)
        public ResponseEntity<ApiError> handleIdempotencyInProgress(IdempotencyRequestInProgressException ex,
                                     HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
            "IDEMPOTENCY_REQUEST_IN_PROGRESS",
            "An idempotent request with this key is currently being processed",
            request,
            List.of(ex.getMessage()));
        }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(PaymentNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND,
                "PAYMENT_NOT_FOUND",
                "Payment was not found",
                request,
                List.of(ex.getMessage()));
    }

    @ExceptionHandler(InvalidPaymentStateException.class)
    public ResponseEntity<ApiError> handleInvalidState(InvalidPaymentStateException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_PAYMENT_STATE",
                "Payment transition is not allowed",
                request,
                List.of(ex.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleConcurrentModification(ObjectOptimisticLockingFailureException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION",
                "Payment was modified concurrently, please retry",
                request,
                List.of(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                request,
                List.of(ex.getMessage()));
    }

    private ResponseEntity<ApiError> build(HttpStatus status,
                                           String code,
                                           String message,
                                           HttpServletRequest request,
                                           List<String> details) {
        ApiError error = new ApiError();
        error.setTimestamp(OffsetDateTime.now());
        error.setStatus(status.value());
        error.setErrorCode(code);
        error.setMessage(message);
        error.setPath(request.getRequestURI());
        error.setDetails(details);

        return ResponseEntity.status(status).body(error);
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}

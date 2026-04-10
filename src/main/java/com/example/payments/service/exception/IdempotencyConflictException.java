package com.example.payments.service.exception;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String operation, String key) {
        super("Idempotency key conflict for operation " + operation + " and key " + key);
    }
}

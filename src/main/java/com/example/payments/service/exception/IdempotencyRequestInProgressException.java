package com.example.payments.service.exception;

public class IdempotencyRequestInProgressException extends RuntimeException {

    public IdempotencyRequestInProgressException(String operation, String key) {
        super("Idempotency request in progress for operation " + operation + " and key " + key);
    }
}

package com.example.payments.domain;

public enum IdempotencyOperation {
    CREATE_PAYMENT,
    AUTHORIZE_PAYMENT,
    SETTLE_PAYMENT
}

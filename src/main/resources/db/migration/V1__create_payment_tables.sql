CREATE TABLE payments (
    id UUID PRIMARY KEY,
    merchant_reference VARCHAR(128) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    authorized_at TIMESTAMP WITH TIME ZONE,
    settled_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_merchant_reference ON payments (merchant_reference);

CREATE TABLE idempotency_records (
    id BIGSERIAL PRIMARY KEY,
    operation VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    payment_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_idempotency_operation_key UNIQUE (operation, idempotency_key),
    CONSTRAINT fk_idempotency_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX idx_idempotency_payment_id ON idempotency_records (payment_id);

package com.example.payments.repository;

import com.example.payments.domain.IdempotencyOperation;
import com.example.payments.domain.IdempotencyRecord;
import com.example.payments.domain.IdempotencyRecordStatus;
import com.example.payments.domain.Payment;
import com.example.payments.domain.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class IdempotencyRecordRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Test
    void shouldSaveAndLoadIdempotencyRecord() {
        Payment payment = persistPayment();

        IdempotencyRecord record = new IdempotencyRecord();
        record.setOperation(IdempotencyOperation.CREATE_PAYMENT);
        record.setIdempotencyKey("idem-a");
        record.setStatus(IdempotencyRecordStatus.COMPLETED);
        record.setPaymentId(payment.getId());
        record.setCreatedAt(OffsetDateTime.now());
        record.setUpdatedAt(OffsetDateTime.now());

        idempotencyRecordRepository.saveAndFlush(record);

        Optional<IdempotencyRecord> loaded = idempotencyRecordRepository
                .findByOperationAndIdempotencyKey(IdempotencyOperation.CREATE_PAYMENT, "idem-a");

        assertTrue(loaded.isPresent());
        assertEquals(payment.getId(), loaded.get().getPaymentId());
    }

    @Test
    void shouldEnforceUniqueOperationAndIdempotencyKey() {
        Payment payment = persistPayment();

        IdempotencyRecord first = new IdempotencyRecord();
        first.setOperation(IdempotencyOperation.AUTHORIZE_PAYMENT);
        first.setIdempotencyKey("idem-duplicate");
        first.setStatus(IdempotencyRecordStatus.PROCESSING);
        first.setPaymentId(payment.getId());
        first.setCreatedAt(OffsetDateTime.now());
        first.setUpdatedAt(OffsetDateTime.now());

        IdempotencyRecord second = new IdempotencyRecord();
        second.setOperation(IdempotencyOperation.AUTHORIZE_PAYMENT);
        second.setIdempotencyKey("idem-duplicate");
        second.setStatus(IdempotencyRecordStatus.COMPLETED);
        second.setPaymentId(payment.getId());
        second.setCreatedAt(OffsetDateTime.now());
        second.setUpdatedAt(OffsetDateTime.now());

        idempotencyRecordRepository.saveAndFlush(first);

        assertThrows(DataIntegrityViolationException.class,
                () -> idempotencyRecordRepository.saveAndFlush(second));
    }

    private Payment persistPayment() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setMerchantReference("ORDER-3003");
        payment.setAmount(new BigDecimal("12.99"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.CREATED);
        payment.setCreatedAt(OffsetDateTime.now());
        payment.setUpdatedAt(OffsetDateTime.now());
        return paymentRepository.saveAndFlush(payment);
    }
}

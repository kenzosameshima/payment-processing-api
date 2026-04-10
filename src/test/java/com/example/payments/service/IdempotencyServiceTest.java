package com.example.payments.service;

import com.example.payments.domain.IdempotencyOperation;
import com.example.payments.domain.IdempotencyRecord;
import com.example.payments.domain.Payment;
import com.example.payments.repository.IdempotencyRecordRepository;
import com.example.payments.repository.PaymentRepository;
import com.example.payments.service.exception.IdempotencyConflictException;
import com.example.payments.service.exception.PaymentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Mock
    private PaymentRepository paymentRepository;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(idempotencyRecordRepository, paymentRepository);
    }

    @Test
    void shouldReturnExistingPaymentForIdempotentReplay() {
        UUID paymentId = UUID.randomUUID();

        IdempotencyRecord record = new IdempotencyRecord();
        record.setOperation(IdempotencyOperation.CREATE_PAYMENT);
        record.setIdempotencyKey("idem-1");
        record.setRequestHash("same-hash");
        record.setPaymentId(paymentId);

        Payment existingPayment = new Payment();
        existingPayment.setId(paymentId);

        when(idempotencyRecordRepository.findByOperationAndIdempotencyKey(IdempotencyOperation.CREATE_PAYMENT, "idem-1"))
                .thenReturn(Optional.of(record));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(existingPayment));

        Payment result = idempotencyService.execute(
                IdempotencyOperation.CREATE_PAYMENT,
                "idem-1",
                "same-hash",
                Payment::new
        );

        assertEquals(paymentId, result.getId());
        verify(idempotencyRecordRepository, times(1))
                .findByOperationAndIdempotencyKey(IdempotencyOperation.CREATE_PAYMENT, "idem-1");
    }

    @Test
    void shouldRejectReplayWhenPayloadHashDiffers() {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setOperation(IdempotencyOperation.CREATE_PAYMENT);
        record.setIdempotencyKey("idem-1");
        record.setRequestHash("old-hash");

        when(idempotencyRecordRepository.findByOperationAndIdempotencyKey(IdempotencyOperation.CREATE_PAYMENT, "idem-1"))
                .thenReturn(Optional.of(record));

        assertThrows(IdempotencyConflictException.class,
                () -> idempotencyService.execute(
                        IdempotencyOperation.CREATE_PAYMENT,
                        "idem-1",
                        "new-hash",
                        Payment::new
                ));
    }

    @Test
    void shouldThrowWhenReplayPaymentNotFound() {
        UUID paymentId = UUID.randomUUID();

        IdempotencyRecord record = new IdempotencyRecord();
        record.setOperation(IdempotencyOperation.AUTHORIZE_PAYMENT);
        record.setIdempotencyKey("idem-auth");
        record.setRequestHash("auth-hash");
        record.setPaymentId(paymentId);

        when(idempotencyRecordRepository.findByOperationAndIdempotencyKey(IdempotencyOperation.AUTHORIZE_PAYMENT, "idem-auth"))
                .thenReturn(Optional.of(record));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class,
                () -> idempotencyService.execute(
                        IdempotencyOperation.AUTHORIZE_PAYMENT,
                        "idem-auth",
                        "auth-hash",
                        Payment::new
                ));
    }

    @Test
    void shouldPersistNewIdempotencyRecordForFreshRequest() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setId(paymentId);

        when(idempotencyRecordRepository.findByOperationAndIdempotencyKey(IdempotencyOperation.SETTLE_PAYMENT, "idem-settle"))
                .thenReturn(Optional.empty());
        when(idempotencyRecordRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = idempotencyService.execute(
                IdempotencyOperation.SETTLE_PAYMENT,
                "idem-settle",
                "settle-hash",
                () -> payment
        );

        assertEquals(paymentId, result.getId());

        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRecordRepository).saveAndFlush(captor.capture());
        assertEquals(IdempotencyOperation.SETTLE_PAYMENT, captor.getValue().getOperation());
        assertEquals("idem-settle", captor.getValue().getIdempotencyKey());
        assertEquals("settle-hash", captor.getValue().getRequestHash());
        assertEquals(paymentId, captor.getValue().getPaymentId());
    }

    @Test
    void shouldHandleConcurrentInsertWithSameHash() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setId(paymentId);

        IdempotencyRecord existing = new IdempotencyRecord();
        existing.setOperation(IdempotencyOperation.CREATE_PAYMENT);
        existing.setIdempotencyKey("idem-c");
        existing.setRequestHash("hash-c");
        existing.setPaymentId(paymentId);
        existing.setCreatedAt(OffsetDateTime.now());

        when(idempotencyRecordRepository.findByOperationAndIdempotencyKey(IdempotencyOperation.CREATE_PAYMENT, "idem-c"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(idempotencyRecordRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        Payment result = idempotencyService.execute(
                IdempotencyOperation.CREATE_PAYMENT,
                "idem-c",
                "hash-c",
                () -> payment
        );

        assertEquals(paymentId, result.getId());
    }
}

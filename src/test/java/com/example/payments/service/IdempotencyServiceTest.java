package com.example.payments.service;

import com.example.payments.domain.IdempotencyOperation;
import com.example.payments.domain.IdempotencyRecord;
import com.example.payments.domain.IdempotencyRecordStatus;
import com.example.payments.domain.Payment;
import com.example.payments.repository.IdempotencyRecordRepository;
import com.example.payments.repository.PaymentRepository;
import com.example.payments.service.exception.IdempotencyConflictException;
import com.example.payments.service.exception.IdempotencyRequestInProgressException;
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
import static org.mockito.Mockito.atLeastOnce;
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
                record.setStatus(IdempotencyRecordStatus.COMPLETED);
        record.setPaymentId(paymentId);

        Payment existingPayment = new Payment();
        existingPayment.setId(paymentId);

        when(idempotencyRecordRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(idempotencyRecordRepository.findByOperationAndIdempotencyKey(IdempotencyOperation.CREATE_PAYMENT, "idem-1"))
                .thenReturn(Optional.of(record));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(existingPayment));

        Payment result = idempotencyService.execute(
                IdempotencyOperation.CREATE_PAYMENT,
                "idem-1",
                Payment::new
        );

        assertEquals(paymentId, result.getId());
        verify(idempotencyRecordRepository, times(1))
                .findByOperationAndIdempotencyKey(IdempotencyOperation.CREATE_PAYMENT, "idem-1");
    }

    @Test
    void shouldRejectReplayWhenRecordHasNoPaymentId() {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setOperation(IdempotencyOperation.CREATE_PAYMENT);
        record.setIdempotencyKey("idem-1");
        record.setStatus(IdempotencyRecordStatus.COMPLETED);

        when(idempotencyRecordRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(idempotencyRecordRepository.findByOperationAndIdempotencyKey(IdempotencyOperation.CREATE_PAYMENT, "idem-1"))
                .thenReturn(Optional.of(record));

        assertThrows(IdempotencyConflictException.class,
                () -> idempotencyService.execute(
                        IdempotencyOperation.CREATE_PAYMENT,
                        "idem-1",
                        Payment::new
                ));
    }

    @Test
    void shouldThrowWhenReplayPaymentNotFound() {
        UUID paymentId = UUID.randomUUID();

        IdempotencyRecord record = new IdempotencyRecord();
        record.setOperation(IdempotencyOperation.AUTHORIZE_PAYMENT);
        record.setIdempotencyKey("idem-auth");
        record.setStatus(IdempotencyRecordStatus.COMPLETED);
        record.setPaymentId(paymentId);

        when(idempotencyRecordRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(idempotencyRecordRepository.findByOperationAndIdempotencyKey(IdempotencyOperation.AUTHORIZE_PAYMENT, "idem-auth"))
                .thenReturn(Optional.of(record));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class,
                () -> idempotencyService.execute(
                        IdempotencyOperation.AUTHORIZE_PAYMENT,
                        "idem-auth",
                        Payment::new
                ));
    }

    @Test
    void shouldPersistNewIdempotencyRecordForFreshRequest() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setId(paymentId);

        when(idempotencyRecordRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = idempotencyService.execute(
                IdempotencyOperation.SETTLE_PAYMENT,
                "idem-settle",
                () -> payment
        );

        assertEquals(paymentId, result.getId());

        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRecordRepository, atLeastOnce()).saveAndFlush(captor.capture());
        assertEquals(IdempotencyOperation.SETTLE_PAYMENT, captor.getValue().getOperation());
        assertEquals("idem-settle", captor.getValue().getIdempotencyKey());
        assertEquals(IdempotencyRecordStatus.COMPLETED, captor.getValue().getStatus());
        assertEquals(paymentId, captor.getValue().getPaymentId());
    }

    @Test
    void shouldReturnConflictWhenRecordIsStillProcessing() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setId(paymentId);

        IdempotencyRecord existing = new IdempotencyRecord();
        existing.setOperation(IdempotencyOperation.CREATE_PAYMENT);
        existing.setIdempotencyKey("idem-c");
        existing.setStatus(IdempotencyRecordStatus.PROCESSING);
        existing.setCreatedAt(OffsetDateTime.now());

        when(idempotencyRecordRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(idempotencyRecordRepository.findByOperationAndIdempotencyKey(IdempotencyOperation.CREATE_PAYMENT, "idem-c"))
                .thenReturn(Optional.of(existing));

        assertThrows(IdempotencyRequestInProgressException.class,
                () -> idempotencyService.execute(
                        IdempotencyOperation.CREATE_PAYMENT,
                        "idem-c",
                        () -> payment
                ));
    }

    @Test
    void shouldDeleteProcessingRecordWhenBusinessFails() {
        when(idempotencyRecordRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(IllegalStateException.class,
                () -> idempotencyService.execute(
                        IdempotencyOperation.CREATE_PAYMENT,
                        "idem-fail",
                        () -> {
                            throw new IllegalStateException("boom");
                        }
                ));

        verify(idempotencyRecordRepository).flush();
    }
}

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
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final PaymentRepository paymentRepository;

    public IdempotencyService(IdempotencyRecordRepository idempotencyRecordRepository,
                              PaymentRepository paymentRepository) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment execute(IdempotencyOperation operation,
                           String idempotencyKey,
                           Supplier<Payment> operationHandler) {
        IdempotencyRecord processingRecord = tryCreateProcessingRecord(operation, idempotencyKey);
        if (processingRecord == null) {
            IdempotencyRecord existing = idempotencyRecordRepository
                    .findByOperationAndIdempotencyKey(operation, idempotencyKey)
                    .orElseThrow(() -> new IdempotencyConflictException(operation.name(), idempotencyKey));
            return replayExisting(existing, operation, idempotencyKey);
        }

        try {
            Payment payment = operationHandler.get();
            completeRecord(processingRecord, payment.getId());
            return payment;
        } catch (RuntimeException ex) {
            // If business logic fails, remove the in-progress marker so clients can retry safely.
            idempotencyRecordRepository.delete(processingRecord);
            idempotencyRecordRepository.flush();
            throw ex;
        }
    }

    private Payment replayExisting(IdempotencyRecord existing,
                                   IdempotencyOperation operation,
                                   String idempotencyKey) {
        if (existing.getStatus() == IdempotencyRecordStatus.PROCESSING) {
            throw new IdempotencyRequestInProgressException(operation.name(), idempotencyKey);
        }

        UUID paymentId = existing.getPaymentId();
        if (paymentId == null) {
            throw new IdempotencyConflictException(operation.name(), idempotencyKey);
        }

        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private IdempotencyRecord tryCreateProcessingRecord(IdempotencyOperation operation, String idempotencyKey) {
        OffsetDateTime now = OffsetDateTime.now();
        IdempotencyRecord record = new IdempotencyRecord();
        record.setOperation(operation);
        record.setIdempotencyKey(idempotencyKey);
        record.setStatus(IdempotencyRecordStatus.PROCESSING);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);

        try {
            return idempotencyRecordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            return null;
        }
    }

    private void completeRecord(IdempotencyRecord record, UUID paymentId) {
        record.setStatus(IdempotencyRecordStatus.COMPLETED);
        record.setPaymentId(paymentId);
        record.setUpdatedAt(OffsetDateTime.now());
        idempotencyRecordRepository.saveAndFlush(record);
    }
}

package com.example.payments.service;

import com.example.payments.domain.IdempotencyOperation;
import com.example.payments.domain.IdempotencyRecord;
import com.example.payments.domain.Payment;
import com.example.payments.repository.IdempotencyRecordRepository;
import com.example.payments.repository.PaymentRepository;
import com.example.payments.service.exception.IdempotencyConflictException;
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
                           String requestHash,
                           Supplier<Payment> operationHandler) {

        IdempotencyRecord existing = idempotencyRecordRepository
                .findByOperationAndIdempotencyKey(operation, idempotencyKey)
                .orElse(null);

        if (existing != null) {
            return replayExisting(existing, operation, idempotencyKey, requestHash);
        }

        Payment payment = operationHandler.get();
        saveRecord(operation, idempotencyKey, requestHash, payment.getId());
        return payment;
    }

    private Payment replayExisting(IdempotencyRecord existing,
                                   IdempotencyOperation operation,
                                   String idempotencyKey,
                                   String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(operation.name(), idempotencyKey);
        }

        UUID paymentId = existing.getPaymentId();
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private void saveRecord(IdempotencyOperation operation,
                            String idempotencyKey,
                            String requestHash,
                            UUID paymentId) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setOperation(operation);
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestHash(requestHash);
        record.setPaymentId(paymentId);
        record.setCreatedAt(OffsetDateTime.now());

        try {
            idempotencyRecordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent retry may insert the same key first. Fetch existing record and validate payload hash.
            IdempotencyRecord existing = idempotencyRecordRepository
                    .findByOperationAndIdempotencyKey(operation, idempotencyKey)
                    .orElseThrow(() -> ex);
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(operation.name(), idempotencyKey);
            }
        }
    }
}

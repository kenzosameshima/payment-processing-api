package com.example.payments.repository;

import com.example.payments.domain.IdempotencyOperation;
import com.example.payments.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByOperationAndIdempotencyKey(IdempotencyOperation operation, String idempotencyKey);
}

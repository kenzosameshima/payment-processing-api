package com.example.payments.service;

import com.example.payments.api.dto.AuthorizePaymentRequest;
import com.example.payments.api.dto.CreatePaymentRequest;
import com.example.payments.api.dto.PaymentResponse;
import com.example.payments.api.dto.SettlePaymentRequest;
import com.example.payments.domain.IdempotencyOperation;
import com.example.payments.domain.Payment;
import com.example.payments.domain.PaymentStatus;
import com.example.payments.repository.PaymentRepository;
import com.example.payments.service.exception.InvalidPaymentStateException;
import com.example.payments.service.exception.PaymentNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;
    private final PaymentMapper paymentMapper;

    public PaymentService(PaymentRepository paymentRepository,
                          IdempotencyService idempotencyService,
                          PaymentMapper paymentMapper) {
        this.paymentRepository = paymentRepository;
        this.idempotencyService = idempotencyService;
        this.paymentMapper = paymentMapper;
    }

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey) {
        Payment payment = idempotencyService.execute(
                IdempotencyOperation.CREATE_PAYMENT,
                idempotencyKey,
                () -> createPaymentInternal(request)
        );

        return paymentMapper.toResponse(payment);
    }

    @Transactional
    public PaymentResponse authorizePayment(UUID paymentId,
                                            AuthorizePaymentRequest request,
                                            String idempotencyKey) {
        Payment payment = idempotencyService.execute(
                IdempotencyOperation.AUTHORIZE_PAYMENT,
                idempotencyKey,
                () -> authorizePaymentInternal(paymentId)
        );

        return paymentMapper.toResponse(payment);
    }

    @Transactional
    public PaymentResponse settlePayment(UUID paymentId,
                                         SettlePaymentRequest request,
                                         String idempotencyKey) {
        Payment payment = idempotencyService.execute(
                IdempotencyOperation.SETTLE_PAYMENT,
                idempotencyKey,
                () -> settlePaymentInternal(paymentId)
        );

        return paymentMapper.toResponse(payment);
    }

    private Payment createPaymentInternal(CreatePaymentRequest request) {
        OffsetDateTime now = OffsetDateTime.now();

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setMerchantReference(request.getMerchantReference());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency().toUpperCase(Locale.ROOT));
        payment.setStatus(PaymentStatus.CREATED);
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);

        return paymentRepository.save(payment);
    }

    private Payment authorizePaymentInternal(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (payment.getStatus() != PaymentStatus.CREATED) {
            throw new InvalidPaymentStateException("Only CREATED payments can be authorized");
        }

        OffsetDateTime now = OffsetDateTime.now();
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAuthorizedAt(now);
        payment.setUpdatedAt(now);

        return paymentRepository.save(payment);
    }

    private Payment settlePaymentInternal(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new InvalidPaymentStateException("Only AUTHORIZED payments can be settled");
        }

        OffsetDateTime now = OffsetDateTime.now();
        payment.setStatus(PaymentStatus.SETTLED);
        payment.setSettledAt(now);
        payment.setUpdatedAt(now);

        return paymentRepository.save(payment);
    }
}

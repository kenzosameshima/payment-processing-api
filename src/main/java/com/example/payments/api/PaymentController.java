package com.example.payments.api;

import com.example.payments.api.dto.AuthorizePaymentRequest;
import com.example.payments.api.dto.CreatePaymentRequest;
import com.example.payments.api.dto.PaymentResponse;
import com.example.payments.api.dto.SettlePaymentRequest;
import com.example.payments.service.PaymentService;
import com.example.payments.service.exception.MissingIdempotencyKeyException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(@Valid @RequestBody CreatePaymentRequest request,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return paymentService.createPayment(request, requireIdempotencyKey(idempotencyKey));
    }

    @PostMapping("/{paymentId}/authorize")
    public PaymentResponse authorizePayment(@PathVariable UUID paymentId,
                                            @Valid @RequestBody AuthorizePaymentRequest request,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return paymentService.authorizePayment(paymentId, request, requireIdempotencyKey(idempotencyKey));
    }

    @PostMapping("/{paymentId}/settle")
    public PaymentResponse settlePayment(@PathVariable UUID paymentId,
                                         @Valid @RequestBody SettlePaymentRequest request,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return paymentService.settlePayment(paymentId, request, requireIdempotencyKey(idempotencyKey));
    }

    private String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException();
        }
        return idempotencyKey;
    }
}

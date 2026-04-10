package com.example.payments.service;

import com.example.payments.api.dto.PaymentResponse;
import com.example.payments.domain.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getId());
        response.setMerchantReference(payment.getMerchantReference());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        response.setStatus(payment.getStatus());
        response.setCreatedAt(payment.getCreatedAt());
        response.setAuthorizedAt(payment.getAuthorizedAt());
        response.setSettledAt(payment.getSettledAt());
        response.setUpdatedAt(payment.getUpdatedAt());
        return response;
    }
}

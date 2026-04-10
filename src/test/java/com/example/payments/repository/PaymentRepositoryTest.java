package com.example.payments.repository;

import com.example.payments.domain.Payment;
import com.example.payments.domain.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void shouldSaveAndLoadPayment() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setMerchantReference("ORDER-2002");
        payment.setAmount(new BigDecimal("42.10"));
        payment.setCurrency("EUR");
        payment.setStatus(PaymentStatus.CREATED);
        payment.setCreatedAt(OffsetDateTime.now());
        payment.setUpdatedAt(OffsetDateTime.now());

        paymentRepository.save(payment);

        Optional<Payment> loaded = paymentRepository.findById(payment.getId());

        assertTrue(loaded.isPresent());
        assertEquals("ORDER-2002", loaded.get().getMerchantReference());
        assertEquals(PaymentStatus.CREATED, loaded.get().getStatus());
    }
}

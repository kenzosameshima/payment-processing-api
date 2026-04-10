package com.example.payments.service;

import com.example.payments.api.dto.AuthorizePaymentRequest;
import com.example.payments.api.dto.CreatePaymentRequest;
import com.example.payments.api.dto.PaymentResponse;
import com.example.payments.api.dto.SettlePaymentRequest;
import com.example.payments.domain.PaymentStatus;
import com.example.payments.repository.IdempotencyRecordRepository;
import com.example.payments.repository.PaymentRepository;
import com.example.payments.service.exception.InvalidPaymentStateException;
import com.example.payments.service.exception.PaymentNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import({PaymentService.class, IdempotencyService.class, PaymentMapper.class})
@ActiveProfiles("test")
class PaymentServiceTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private PaymentService paymentService;

    @Test
    void createPaymentShouldPersistAndReturnResponse() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setMerchantReference("ORDER-1001");
        request.setAmount(new BigDecimal("125.20"));
        request.setCurrency("USD");

        PaymentResponse response = paymentService.createPayment(request, "idem-1");

        assertNotNull(response.getPaymentId());
        assertEquals("ORDER-1001", response.getMerchantReference());
        assertEquals(new BigDecimal("125.20"), response.getAmount());
        assertEquals("USD", response.getCurrency());
        assertEquals(PaymentStatus.CREATED, response.getStatus());
        assertNotNull(response.getCreatedAt());
    }

    @Test
    void authorizePaymentShouldUpdateStatusWhenCreated() {
        // Setup: Create a payment in CREATED state
        CreatePaymentRequest createRequest = new CreatePaymentRequest();
        createRequest.setMerchantReference("ORDER-2002");
        createRequest.setAmount(new BigDecimal("100.00"));
        createRequest.setCurrency("USD");
        PaymentResponse createResponse = paymentService.createPayment(createRequest, "idem-create");
        UUID paymentId = createResponse.getPaymentId();

        // Act: Authorize the payment
        AuthorizePaymentRequest authRequest = new AuthorizePaymentRequest();
        authRequest.setAuthorizationCode("AUTH-001");
        PaymentResponse authResponse = paymentService.authorizePayment(paymentId, authRequest, "idem-auth");

        // Assert: Status should be AUTHORIZED
        assertEquals(PaymentStatus.AUTHORIZED, authResponse.getStatus());
        assertNotNull(authResponse.getAuthorizedAt());
        assertEquals(paymentId, authResponse.getPaymentId());
    }

    @Test
    void authorizePaymentShouldFailWhenPaymentNotInCreatedState() {
        // Setup: Create and authorize a payment
        CreatePaymentRequest createRequest = new CreatePaymentRequest();
        createRequest.setMerchantReference("ORDER-3003");
        createRequest.setAmount(new BigDecimal("50.00"));
        createRequest.setCurrency("GBP");
        PaymentResponse createResponse = paymentService.createPayment(createRequest, "idem-create-3");
        UUID paymentId = createResponse.getPaymentId();

        AuthorizePaymentRequest authRequest = new AuthorizePaymentRequest();
        authRequest.setAuthorizationCode("AUTH-002");
        paymentService.authorizePayment(paymentId, authRequest, "idem-auth-3");

        // Act & Assert: Try to authorize again, should fail
        assertThrows(InvalidPaymentStateException.class,
                () -> paymentService.authorizePayment(paymentId, authRequest, "idem-auth-3-again"));
    }

    @Test
    void settlePaymentShouldUpdateStatusWhenAuthorized() {
        // Setup: Create and authorize a payment
        CreatePaymentRequest createRequest = new CreatePaymentRequest();
        createRequest.setMerchantReference("ORDER-4004");
        createRequest.setAmount(new BigDecimal("75.50"));
        createRequest.setCurrency("EUR");
        PaymentResponse createResponse = paymentService.createPayment(createRequest, "idem-create-4");
        UUID paymentId = createResponse.getPaymentId();

        AuthorizePaymentRequest authRequest = new AuthorizePaymentRequest();
        authRequest.setAuthorizationCode("AUTH-003");
        paymentService.authorizePayment(paymentId, authRequest, "idem-auth-4");

        // Act: Settle the payment
        SettlePaymentRequest settleRequest = new SettlePaymentRequest();
        settleRequest.setSettlementReference("SET-001");
        PaymentResponse settleResponse = paymentService.settlePayment(paymentId, settleRequest, "idem-settle-4");

        // Assert: Status should be SETTLED
        assertEquals(PaymentStatus.SETTLED, settleResponse.getStatus());
        assertNotNull(settleResponse.getSettledAt());
        assertEquals(paymentId, settleResponse.getPaymentId());
    }

    @Test
    void settlePaymentShouldFailWhenMissingPayment() {
        UUID nonExistentPaymentId = UUID.randomUUID();

        SettlePaymentRequest request = new SettlePaymentRequest();
        request.setSettlementReference("SET-002");

        assertThrows(PaymentNotFoundException.class,
                () -> paymentService.settlePayment(nonExistentPaymentId, request, "idem-settle-missing"));
    }
}

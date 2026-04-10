package com.example.payments.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SettlePaymentRequest {

    @NotBlank(message = "settlementReference is required")
    @Size(max = 64, message = "settlementReference must be at most 64 characters")
    private String settlementReference;

    public String getSettlementReference() {
        return settlementReference;
    }

    public void setSettlementReference(String settlementReference) {
        this.settlementReference = settlementReference;
    }
}

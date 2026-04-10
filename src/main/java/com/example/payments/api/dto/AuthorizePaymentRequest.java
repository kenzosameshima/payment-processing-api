package com.example.payments.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthorizePaymentRequest {

    @NotBlank(message = "authorizationCode is required")
    @Size(max = 64, message = "authorizationCode must be at most 64 characters")
    private String authorizationCode;

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public void setAuthorizationCode(String authorizationCode) {
        this.authorizationCode = authorizationCode;
    }
}

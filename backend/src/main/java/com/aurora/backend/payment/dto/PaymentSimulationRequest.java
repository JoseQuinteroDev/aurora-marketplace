package com.aurora.backend.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PaymentSimulationRequest(
        @NotNull(message = "Success flag is required.")
        Boolean success,

        @Size(max = 255, message = "Payment message must be at most 255 characters.")
        String message
) {
}

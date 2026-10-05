package com.guilhermelevi.barbearia.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MetaEmbeddedSignupRequest(
        @NotBlank
        String code,
        @NotBlank
        String wabaId,
        @NotBlank
        String phoneNumberId,
        @NotNull
        UUID token

) {
}

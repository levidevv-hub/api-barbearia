package com.guilhermelevi.barbearia.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MetaAccessTokenResponse(

        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("token_type")
        String tokenType

) {
}
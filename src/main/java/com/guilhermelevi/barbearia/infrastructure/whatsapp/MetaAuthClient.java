package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import com.guilhermelevi.barbearia.dto.response.MetaAccessTokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MetaAuthClient {

    private final RestClient restClient;

    @Value("${meta.app-id}")
    private String appId;

    @Value("${meta.app-secret}")
    private String appSecret;

    @Value("${whatsapp.api-version}")
    private String apiVersion;

    public MetaAuthClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://graph.facebook.com")
                .build();
    }

    public MetaAccessTokenResponse trocarCodePorToken(String code) {

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{version}/oauth/access_token")
                        .queryParam("client_id", appId)
                        .queryParam("client_secret", appSecret)
                        .queryParam("code", code)
                        .build(apiVersion)
                )
                .retrieve()
                .body(MetaAccessTokenResponse.class);
    }

    public void inscreverWebhook(
            String wabaId,
            String accessToken
    ) {

        restClient.post()
                .uri(
                        "/{version}/{wabaId}/subscribed_apps",
                        apiVersion,
                        wabaId
                )
                .header(
                        "Authorization",
                        "Bearer " + accessToken
                )
                .retrieve()
                .toBodilessEntity();
    }
}
package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class ValidadorAssinaturaWebhook {

    private final byte[] appSecret;

    public ValidadorAssinaturaWebhook(
            @Value("${WHATSAPP_APP_SECRET}") String appSecret
    ) {
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException(
                    "Configure o App Secret da Meta."
            );
        }

        this.appSecret = appSecret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean validar(byte[] corpo, String assinatura) {
        if (corpo == null || assinatura == null
                || !assinatura.matches("sha256=[0-9a-fA-F]{64}")) {
            return false;
        }

        byte[] assinaturaRecebida = HexFormat.of()
                .parseHex(assinatura.substring("sha256=".length()));

        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            mac.init(new SecretKeySpec(appSecret, "HmacSHA256"));

            byte[] assinaturaCalculada = mac.doFinal(corpo);

            return MessageDigest.isEqual(
                    assinaturaCalculada,
                    assinaturaRecebida
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Não foi possível validar a assinatura do webhook.",
                    e
            );
        }
    }
}
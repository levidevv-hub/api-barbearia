package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.ConexaoWhatsAppPendente;
import com.guilhermelevi.barbearia.dto.request.MetaEmbeddedSignupRequest;
import com.guilhermelevi.barbearia.dto.response.MetaAccessTokenResponse;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.MetaAuthClient;
import org.springframework.stereotype.Service;
import com.guilhermelevi.barbearia.dto.request.MetaEmbeddedSignupRequest;
import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;

@Service
public class MetaWhatsAppService {

    private final MetaAuthClient metaAuthClient;
    private final IBarbeiroRepository barbeiroRepository;
    private final ConexaoWhatsAppPendenteService conexaoWhatsAppPendenteService;

    public MetaWhatsAppService(MetaAuthClient metaAuthClient, IBarbeiroRepository barbeiroRepository, ConexaoWhatsAppPendenteService conexaoWhatsAppPendenteService) {
        this.metaAuthClient = metaAuthClient;
        this.barbeiroRepository = barbeiroRepository;
        this.conexaoWhatsAppPendenteService = conexaoWhatsAppPendenteService;
    }

    public void conectar(MetaEmbeddedSignupRequest request) {

        ConexaoWhatsAppPendente conexao =
                conexaoWhatsAppPendenteService.buscarValida(
                        request.token()
                );

        Barbeiro barbeiro = conexao.getBarbeiro();

        MetaAccessTokenResponse response =
                metaAuthClient.trocarCodePorToken(request.code());

        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException(
                    "A Meta não retornou um access token."
            );
        }

        metaAuthClient.inscreverWebhook(
                request.wabaId(),
                response.accessToken()
        );

        barbeiro.setWhatsappWabaId(request.wabaId());
        barbeiro.setWhatsappPhoneNumberId(request.phoneNumberId());
        barbeiro.setWhatsappAccessToken(response.accessToken());
        barbeiroRepository.save(barbeiro);
        conexaoWhatsAppPendenteService.marcarComoUsada(conexao);

        if (
                request.wabaId() == null ||
                        request.wabaId().isBlank() ||
                        request.phoneNumberId() == null ||
                        request.phoneNumberId().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "WABA ID ou Phone Number ID não informado."
            );
        }

        System.out.println("Token obtido com sucesso.");
    }
}
package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.StatusMensagemRecebido;
import com.guilhermelevi.barbearia.repositories.IStatusMensagemRecebidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecebimentoStatusService {

    private final IStatusMensagemRecebidoRepository repository;

    @Transactional
    public void receber(JsonNode payload) {
        JsonNode entradas = payload.path("entry");

        if (!entradas.isArray()) {
            return;
        }

        for (JsonNode entrada : entradas) {
            JsonNode alteracoes = entrada.path("changes");

            if (!alteracoes.isArray()) {
                continue;
            }

            for (JsonNode alteracao : alteracoes) {
                JsonNode valor = alteracao.path("value");
                JsonNode statuses = valor.path("statuses");

                if (!statuses.isArray()) {
                    continue;
                }

                String phoneNumberId = valor.path("metadata")
                        .path("phone_number_id")
                        .asText("");

                for (JsonNode statusRecebido : statuses) {
                    salvarStatus(phoneNumberId, statusRecebido);
                }
            }
        }
    }

    private void salvarStatus(
            String phoneNumberId,
            JsonNode statusRecebido
    ) {
        String mensagemId = statusRecebido.path("id").asText("");
        String status = statusRecebido.path("status").asText("");

        if (phoneNumberId.isBlank()
                || mensagemId.isBlank()
                || status.isBlank()) {
            log.warn("Status de mensagem recebido com campos obrigatórios ausentes.");
            return;
        }

        // Guardamos os eventos que indicam entrega ou falha.
        if (!"delivered".equals(status)
                && !"read".equals(status)
                && !"failed".equals(status)) {
            return;
        }

        JsonNode erros = statusRecebido.path("errors");

        String detalhesErro = erros.isArray() && !erros.isEmpty()
                ? erros.toString()
                : null;

        repository.save(
                new StatusMensagemRecebido(
                        mensagemId,
                        phoneNumberId,
                        status,
                        detalhesErro
                )
        );
    }
}
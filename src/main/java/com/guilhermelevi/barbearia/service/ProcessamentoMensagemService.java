package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.MensagemRecebida;
import com.guilhermelevi.barbearia.repositories.IMensagemRecebidaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class ProcessamentoMensagemService {

    private final IMensagemRecebidaRepository repository;
    private final ConversaService conversaService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void processar(JsonNode payload) {
        JsonNode valor = payload.path("entry")
                .path(0)
                .path("changes")
                .path(0)
                .path("value");

        JsonNode mensagens = valor.path("messages");

        if (!mensagens.isArray() || mensagens.isEmpty()) {
            return;
        }

        JsonNode mensagem = mensagens.get(0);

        String phoneNumberId = valor.path("metadata")
                .path("phone_number_id")
                .asText("");

        String mensagemId = mensagem.path("id").asText("");
        String remetente = mensagem.path("from").asText("");

        if (phoneNumberId.isBlank()
                || mensagemId.isBlank()
                || remetente.isBlank()) {
            throw new IllegalArgumentException(
                    "Mensagem recebida sem identificação obrigatória."
            );
        }

        repository.registrarSeNova(
                phoneNumberId,
                mensagemId,
                remetente,
                payload.toString()
        );

        MensagemRecebida registro = repository.buscarParaProcessar(
                        phoneNumberId,
                        mensagemId
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Não foi possível localizar a mensagem registrada."
                ));

        if (registro.getStatus() == MensagemRecebida.Status.PROCESSADA) {
            return;
        }

        registro.iniciarProcessamento();

        conversaService.processar(payload);

        registro.concluirProcessamento();
    }
}
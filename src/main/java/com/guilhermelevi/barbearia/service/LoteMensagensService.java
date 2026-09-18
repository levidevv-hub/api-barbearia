package com.guilhermelevi.barbearia.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class LoteMensagensService {

    private final ObjectMapper objectMapper;
    private final ProcessamentoMensagemService processamentoService;

    public void processar(JsonNode payload) {
        if (!payload.isObject() || !payload.path("entry").isArray()) {
            return;
        }

        for (JsonNode entrada : payload.path("entry")) {
            if (!entrada.isObject()
                    || !entrada.path("changes").isArray()) {
                continue;
            }

            for (JsonNode alteracao : entrada.path("changes")) {
                JsonNode valor = alteracao.path("value");

                if (!alteracao.isObject()
                        || !valor.isObject()
                        || !valor.path("messages").isArray()) {
                    continue;
                }

                for (JsonNode mensagem : valor.path("messages")) {
                    ObjectNode valorIndividual =
                            ((ObjectNode) valor).deepCopy();

                    valorIndividual.set(
                            "messages",
                            objectMapper.createArrayNode().add(mensagem)
                    );

                    ObjectNode alteracaoIndividual =
                            ((ObjectNode) alteracao).deepCopy();

                    alteracaoIndividual.set("value", valorIndividual);

                    ObjectNode entradaIndividual =
                            ((ObjectNode) entrada).deepCopy();

                    entradaIndividual.set(
                            "changes",
                            objectMapper.createArrayNode()
                                    .add(alteracaoIndividual)
                    );

                    ObjectNode payloadIndividual =
                            ((ObjectNode) payload).deepCopy();

                    payloadIndividual.set(
                            "entry",
                            objectMapper.createArrayNode()
                                    .add(entradaIndividual)
                    );

                    processamentoService.processar(payloadIndividual);
                }
            }
        }
    }
}
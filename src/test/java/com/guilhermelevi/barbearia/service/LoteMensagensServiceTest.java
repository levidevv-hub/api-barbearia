package com.guilhermelevi.barbearia.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoteMensagensServiceTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ProcessamentoMensagemService processamento =
            mock(ProcessamentoMensagemService.class);
    private final LoteMensagensService service =
            new LoteMensagensService(mapper, processamento);

    @Test
    void separaCadaMensagemDoLote() {
        var payload = mapper.readTree("""
                {"entry":[{"changes":[{"value":{
                  "messages":[
                    {"id":"1","from":"a"},
                    {"id":"2","from":"b"}
                  ]
                }}]}]}
                """);

        service.processar(payload);

        verify(processamento, times(2)).processar(any());
    }

    @Test
    void ignoraPayloadSemEstruturaDeMensagens() {
        service.processar(mapper.readTree("{\"entry\":{}}"));

        verifyNoInteractions(processamento);
    }
}

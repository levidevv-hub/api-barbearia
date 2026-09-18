package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.StatusMensagemRecebido;
import com.guilhermelevi.barbearia.repositories.IStatusMensagemRecebidoRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RecebimentoStatusServiceTest {

    private final IStatusMensagemRecebidoRepository repository =
            mock(IStatusMensagemRecebidoRepository.class);
    private final RecebimentoStatusService service =
            new RecebimentoStatusService(repository);
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void salvaSomenteStatusesRelevantes() {
        var payload = mapper.readTree("""
                {"entry":[{"changes":[{"value":{
                  "metadata":{"phone_number_id":"phone"},
                  "statuses":[
                    {"id":"1","status":"sent"},
                    {"id":"2","status":"delivered"},
                    {"id":"3","status":"read"},
                    {"id":"4","status":"failed","errors":[{"code":131000}]}
                  ]
                }}]}]}
                """);

        service.receber(payload);

        verify(repository, times(3)).save(any(StatusMensagemRecebido.class));
    }

    @Test
    void ignoraEstruturaInvalidaECamposAusentes() {
        service.receber(mapper.readTree("{\"object\":\"whatsapp_business_account\"}"));
        service.receber(mapper.readTree("""
                {"entry":[{"changes":[{"value":{
                  "metadata":{"phone_number_id":""},
                  "statuses":[{"id":"","status":"read"}]
                }}]}]}
                """));

        verifyNoInteractions(repository);
    }
}

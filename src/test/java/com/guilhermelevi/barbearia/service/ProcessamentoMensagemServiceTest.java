package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.MensagemRecebida;
import com.guilhermelevi.barbearia.repositories.IMensagemRecebidaRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessamentoMensagemServiceTest {

    private final IMensagemRecebidaRepository repository =
            mock(IMensagemRecebidaRepository.class);
    private final ConversaService conversaService = mock(ConversaService.class);
    private final ProcessamentoMensagemService service =
            new ProcessamentoMensagemService(repository, conversaService);
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void ignoraPayloadSemMensagem() {
        service.processar(mapper.readTree("{\"entry\":[]}"));

        verifyNoInteractions(repository, conversaService);
    }

    @Test
    void rejeitaMensagemSemIdentificadoresObrigatorios() {
        JsonNode payload = mapper.readTree("""
                {"entry":[{"changes":[{"value":{
                  "metadata":{"phone_number_id":"phone"},
                  "messages":[{"from":"","id":""}]
                }}]}]}
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.processar(payload)
        );
    }

    @Test
    void registraProcessaEConcluiMensagemNova() {
        JsonNode payload = payloadValido();
        MensagemRecebida registro = new MensagemRecebida(
                "phone",
                "wamid.1",
                "5588999999999",
                payload.toString()
        );

        when(repository.buscarParaProcessar("phone", "wamid.1"))
                .thenReturn(Optional.of(registro));

        service.processar(payload);

        verify(repository).registrarSeNova(
                eq("phone"),
                eq("wamid.1"),
                eq("5588999999999"),
                anyString()
        );
        verify(conversaService).processar(payload);
        assertEquals(MensagemRecebida.Status.PROCESSADA, registro.getStatus());
    }

    @Test
    void naoReprocessaMensagemJaProcessada() {
        JsonNode payload = payloadValido();
        MensagemRecebida registro = new MensagemRecebida(
                "phone",
                "wamid.1",
                "5588999999999",
                payload.toString()
        );
        registro.iniciarProcessamento();
        registro.concluirProcessamento();

        when(repository.buscarParaProcessar("phone", "wamid.1"))
                .thenReturn(Optional.of(registro));

        service.processar(payload);

        verifyNoInteractions(conversaService);
    }

    private JsonNode payloadValido() {
        return mapper.readTree("""
                {"entry":[{"changes":[{"value":{
                  "metadata":{"phone_number_id":"phone"},
                  "messages":[{"from":"5588999999999","id":"wamid.1"}]
                }}]}]}
                """);
    }
}

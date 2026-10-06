package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.domain.Cliente;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.WhatsAppClient;
import com.guilhermelevi.barbearia.repositories.IServicoRepository;
import com.guilhermelevi.barbearia.repositories.ISessaoConversaRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ConversaAgendamentoServiceTest {

    @Test
    void enviaLocalizacaoQuandoClienteClicaNoBotao() {

        IServicoRepository servicoRepository =
                mock(IServicoRepository.class);

        ISessaoConversaRepository sessaoRepository =
                mock(ISessaoConversaRepository.class);

        DisponibilidadeService disponibilidadeService =
                mock(DisponibilidadeService.class);

        AgendamentoService agendamentoService =
                mock(AgendamentoService.class);

        WhatsAppClient whatsapp =
                mock(WhatsAppClient.class);

        ConversaAgendamentoService service =
                new ConversaAgendamentoService(
                        servicoRepository,
                        sessaoRepository,
                        disponibilidadeService,
                        agendamentoService,
                        whatsapp
                );

        Barbeiro barbeiro = Barbeiro.builder()
                .whatsappPhoneNumberId("phone-123")
                .build();

        Cliente cliente = new Cliente();

        service.processarInteracao(
                "VER_LOCALIZACAO",
                barbeiro,
                cliente,
                "5588999999999"
        );

        verify(whatsapp).enviarLocalizacao(
                "phone-123",
                "5588999999999",
                barbeiro
        );

        verifyNoMoreInteractions(whatsapp);
    }
}
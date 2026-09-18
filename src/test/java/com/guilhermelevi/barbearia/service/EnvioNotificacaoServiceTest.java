package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.WhatsAppClient;
import com.guilhermelevi.barbearia.repositories.INotificacaoPendenteRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnvioNotificacaoServiceTest {

    @Test
    void enviaTextoEMarcaAceite() throws Exception {
        INotificacaoPendenteRepository repository =
                mock(INotificacaoPendenteRepository.class);
        WhatsAppClient whatsapp = mock(WhatsAppClient.class);
        EnvioNotificacaoService service =
                new EnvioNotificacaoService(repository, whatsapp);
        definirUsarTemplate(service, false);

        NotificacaoPendente notificacao = notificacao();
        when(repository.buscarParaEnviar(1L))
                .thenReturn(Optional.of(notificacao));
        when(whatsapp.enviarTexto(
                notificacao.getPhoneNumberId(),
                notificacao.getDestinatario(),
                notificacao.getMensagem()
        )).thenReturn("wamid.1");

        service.enviarPendente(1L);

        assertEquals(1, notificacao.getTentativas());
        assertEquals(
                NotificacaoPendente.Status.ACEITA_PELA_API,
                notificacao.getStatus()
        );
        assertEquals("wamid.1", notificacao.getMensagemIdMeta());
    }

    @Test
    void falhaDoClienteMarcaNotificacaoComoFalha() throws Exception {
        INotificacaoPendenteRepository repository =
                mock(INotificacaoPendenteRepository.class);
        WhatsAppClient whatsapp = mock(WhatsAppClient.class);
        EnvioNotificacaoService service =
                new EnvioNotificacaoService(repository, whatsapp);
        definirUsarTemplate(service, false);

        NotificacaoPendente notificacao = notificacao();
        when(repository.buscarParaEnviar(2L))
                .thenReturn(Optional.of(notificacao));
        when(whatsapp.enviarTexto(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("Meta indisponível"));

        service.enviarPendente(2L);

        assertEquals(NotificacaoPendente.Status.FALHA, notificacao.getStatus());
        assertEquals("Meta indisponível", notificacao.getUltimoErro());
    }

    @Test
    void naoReenviaNotificacaoForaDoStatusPendente() throws Exception {
        INotificacaoPendenteRepository repository =
                mock(INotificacaoPendenteRepository.class);
        WhatsAppClient whatsapp = mock(WhatsAppClient.class);
        EnvioNotificacaoService service =
                new EnvioNotificacaoService(repository, whatsapp);
        definirUsarTemplate(service, false);

        NotificacaoPendente notificacao = notificacao();
        notificacao.registrarTentativa();
        notificacao.registrarAceite("wamid.3");

        when(repository.buscarParaEnviar(3L))
                .thenReturn(Optional.of(notificacao));

        service.enviarPendente(3L);

        verifyNoInteractions(whatsapp);
    }

    private static NotificacaoPendente notificacao() {
        Cliente cliente = Cliente.builder()
                .nomeCompleto("Cliente")
                .numeroTelefone("5588999999999")
                .build();
        Barbeiro barbeiro = Barbeiro.builder()
                .nome("Zalura")
                .whatsappPhoneNumberId("phone")
                .build();
        Servico servico = Servico.builder()
                .nome("Corte")
                .duracaoMinutos(30)
                .build();
        Agendamento agendamento = new Agendamento(
                cliente,
                barbeiro,
                servico,
                LocalDateTime.now().plusDays(1)
        );
        return new NotificacaoPendente(agendamento, "Mensagem");
    }

    private static void definirUsarTemplate(
            EnvioNotificacaoService service,
            boolean valor
    ) throws Exception {
        Field campo = EnvioNotificacaoService.class
                .getDeclaredField("usarTemplate");
        campo.setAccessible(true);
        campo.set(service, valor);
    }
}

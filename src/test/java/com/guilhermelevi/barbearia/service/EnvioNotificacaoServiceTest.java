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
    void cancelamentoBarbeiroUsaTemplateCentralMesmoPausadoEPersisteFalha() {
        var repository = mock(INotificacaoPendenteRepository.class);
        var whatsapp = mock(WhatsAppClient.class);
        var central = mock(CentralWhatsappService.class);
        var service = new EnvioNotificacaoService(repository, whatsapp, central);
        var reserva = notificacao().getAgendamento();
        reserva.getBarbeiro().setNumeroWhatsAppNotificacao("5588991111111");
        reserva.getBarbeiro().setRoboAtivo(false);
        var aviso = NotificacaoPendente.cancelamentoBarbeiro(reserva);
        when(repository.buscarParaEnviar(1L)).thenReturn(Optional.of(aviso));
        when(central.linhaNotificacao(anyString())).thenReturn("central");
        when(whatsapp.enviarTemplate(anyString(),anyString(),anyString(),anyList()))
                .thenThrow(new IllegalStateException("132001 template não encontrado"));
        service.enviarPendente(1L);
        assertEquals(NotificacaoPendente.Status.FALHA, aviso.getStatus());
        assertTrue(aviso.getUltimoErro().contains("132001"));
        assertEquals(1,aviso.getTentativas());
        service.enviarPendente(1L);
        assertEquals(1,aviso.getTentativas()); // Sem repetição silenciosa após falha.
        aviso.solicitarReenvio();
        doReturn("wamid.cancelado").when(whatsapp).enviarTemplate(anyString(),anyString(),anyString(),anyList());
        service.enviarPendente(1L);
        assertEquals(NotificacaoPendente.Status.ACEITA_PELA_API,aviso.getStatus());
        verify(whatsapp,times(2)).enviarTemplate(eq("central"),eq("5588991111111"),eq("agendamento_cancelado_barbeiro"),argThat(d -> d.size()==5));
        verify(whatsapp,never()).enviarTexto(anyString(),anyString(),anyString());
    }

    @Test
    void pausaMantemAvisoPendenteSemTentativasEReativacaoEnvia() {
        var repository = mock(INotificacaoPendenteRepository.class);
        var whatsapp = mock(WhatsAppClient.class);
        var service = new EnvioNotificacaoService(repository, whatsapp, mock(CentralWhatsappService.class));
        var notificacao = notificacao();
        var barbeiro = notificacao.getAgendamento().getBarbeiro();
        barbeiro.setRoboAtivo(false);
        when(repository.buscarParaEnviar(1L)).thenReturn(Optional.of(notificacao));

        service.enviarPendente(1L);

        verifyNoInteractions(whatsapp);
        assertEquals(NotificacaoPendente.Status.PENDENTE, notificacao.getStatus());
        assertEquals(0, notificacao.getTentativas());
        assertNull(notificacao.getUltimaTentativaEm());

        barbeiro.setRoboAtivo(true);
        when(whatsapp.enviarTexto(anyString(), anyString(), anyString())).thenReturn("wamid.reativado");
        service.enviarPendente(1L);
        assertEquals(NotificacaoPendente.Status.ACEITA_PELA_API, notificacao.getStatus());
        assertEquals(1, notificacao.getTentativas());
    }

    @Test
    void enviaTextoEMarcaAceite() throws Exception {
        INotificacaoPendenteRepository repository =
                mock(INotificacaoPendenteRepository.class);
        WhatsAppClient whatsapp = mock(WhatsAppClient.class);
        EnvioNotificacaoService service =
                new EnvioNotificacaoService(repository, whatsapp, mock(CentralWhatsappService.class));
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
                new EnvioNotificacaoService(repository, whatsapp, mock(CentralWhatsappService.class));
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
                new EnvioNotificacaoService(repository, whatsapp, mock(CentralWhatsappService.class));
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

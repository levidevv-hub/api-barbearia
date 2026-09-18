package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.repositories.INotificacaoPendenteRepository;
import com.guilhermelevi.barbearia.repositories.IStatusMensagemRecebidoRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessamentoStatusServiceTest {

    private final IStatusMensagemRecebidoRepository statusRepository =
            mock(IStatusMensagemRecebidoRepository.class);
    private final INotificacaoPendenteRepository notificacaoRepository =
            mock(INotificacaoPendenteRepository.class);
    private final ProcessamentoStatusService service =
            new ProcessamentoStatusService(statusRepository, notificacaoRepository);

    @Test
    void deliveredMarcaNotificacaoEntregueEEventoProcessado() {
        StatusMensagemRecebido evento = new StatusMensagemRecebido(
                "wamid.1", "phone", "delivered", null
        );
        NotificacaoPendente notificacao = notificacao();
        notificacao.registrarAceite("wamid.1");

        when(statusRepository.buscarParaProcessar(1L))
                .thenReturn(Optional.of(evento));
        when(notificacaoRepository.findByMensagemIdMeta("wamid.1"))
                .thenReturn(Optional.of(notificacao));

        service.processar(1L);

        assertTrue(evento.isProcessado());
        assertEquals(NotificacaoPendente.Status.ENTREGUE, notificacao.getStatus());
    }

    @Test
    void failedMarcaFalha() {
        StatusMensagemRecebido evento = new StatusMensagemRecebido(
                "wamid.2", "phone", "failed", "erro-meta"
        );
        NotificacaoPendente notificacao = notificacao();
        notificacao.registrarAceite("wamid.2");

        when(statusRepository.buscarParaProcessar(2L))
                .thenReturn(Optional.of(evento));
        when(notificacaoRepository.findByMensagemIdMeta("wamid.2"))
                .thenReturn(Optional.of(notificacao));

        service.processar(2L);

        assertEquals(NotificacaoPendente.Status.FALHA, notificacao.getStatus());
        assertEquals("erro-meta", notificacao.getUltimoErro());
        assertTrue(evento.isProcessado());
    }

    @Test
    void mantemEventoPendenteQuandoNotificacaoAindaNaoFoiAssociada() {
        StatusMensagemRecebido evento = new StatusMensagemRecebido(
                "wamid.3", "phone", "read", null
        );

        when(statusRepository.buscarParaProcessar(3L))
                .thenReturn(Optional.of(evento));
        when(notificacaoRepository.findByMensagemIdMeta("wamid.3"))
                .thenReturn(Optional.empty());

        service.processar(3L);

        assertFalse(evento.isProcessado());
    }

    @Test
    void contaDiferenteNaoAlteraNotificacaoEMarcaEventoProcessado() {
        StatusMensagemRecebido evento = new StatusMensagemRecebido(
                "wamid.4", "outra-conta", "delivered", null
        );
        NotificacaoPendente notificacao = notificacao();
        notificacao.registrarAceite("wamid.4");

        when(statusRepository.buscarParaProcessar(4L))
                .thenReturn(Optional.of(evento));
        when(notificacaoRepository.findByMensagemIdMeta("wamid.4"))
                .thenReturn(Optional.of(notificacao));

        service.processar(4L);

        assertTrue(evento.isProcessado());
        assertEquals(
                NotificacaoPendente.Status.ACEITA_PELA_API,
                notificacao.getStatus()
        );
    }

    private NotificacaoPendente notificacao() {
        Cliente cliente = Cliente.builder()
                .numeroTelefone("5588999999999")
                .build();
        Barbeiro barbeiro = Barbeiro.builder()
                .whatsappPhoneNumberId("phone")
                .build();
        Servico servico = Servico.builder().duracaoMinutos(30).build();
        Agendamento agendamento = new Agendamento(
                cliente, barbeiro, servico, LocalDateTime.now().plusDays(1)
        );
        return new NotificacaoPendente(agendamento, "Mensagem");
    }
}

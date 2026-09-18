package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.NotificacaoPendente;
import com.guilhermelevi.barbearia.domain.StatusMensagemRecebido;
import com.guilhermelevi.barbearia.repositories.INotificacaoPendenteRepository;
import com.guilhermelevi.barbearia.repositories.IStatusMensagemRecebidoRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SchedulerServiceTest {

    @Test
    void notificacaoSchedulerProcessaTodosMesmoQuandoUmFalha() {
        INotificacaoPendenteRepository repository =
                mock(INotificacaoPendenteRepository.class);
        EnvioNotificacaoService envio = mock(EnvioNotificacaoService.class);

        NotificacaoPendente primeira = mock(NotificacaoPendente.class);
        NotificacaoPendente segunda = mock(NotificacaoPendente.class);

        when(primeira.getId()).thenReturn(1L);
        when(segunda.getId()).thenReturn(2L);
        when(repository.findByStatusOrderByCriadaEmAscIdAsc(
                eq(NotificacaoPendente.Status.PENDENTE),
                any()
        )).thenReturn(List.of(primeira, segunda));

        doThrow(new RuntimeException("falha"))
                .when(envio)
                .enviarPendente(1L);

        new NotificacaoScheduler(repository, envio).processarPendentes();

        verify(envio).enviarPendente(1L);
        verify(envio).enviarPendente(2L);
    }

    @Test
    void statusSchedulerProcessaTodosMesmoQuandoUmFalha() {
        IStatusMensagemRecebidoRepository repository =
                mock(IStatusMensagemRecebidoRepository.class);
        ProcessamentoStatusService processamento =
                mock(ProcessamentoStatusService.class);

        StatusMensagemRecebido primeiro =
                mock(StatusMensagemRecebido.class);
        StatusMensagemRecebido segundo =
                mock(StatusMensagemRecebido.class);

        when(primeiro.getId()).thenReturn(10L);
        when(segundo.getId()).thenReturn(11L);
        when(repository.buscarProntosParaProcessar(any()))
                .thenReturn(List.of(primeiro, segundo));

        doThrow(new RuntimeException("falha"))
                .when(processamento)
                .processar(10L);

        new StatusMensagemScheduler(repository, processamento)
                .processarRecebidos();

        verify(processamento).processar(10L);
        verify(processamento).processar(11L);
    }
}

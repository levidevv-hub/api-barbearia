package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.domain.BloqueioData;
import com.guilhermelevi.barbearia.domain.exception.OperacaoAdministrativaException;
import com.guilhermelevi.barbearia.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BloqueioDataServiceTest {

    private IAgendamentoRepository agendamentos;
    private IBarbeiroRepository barbeiros;
    private IBloqueioDataRepository bloqueios;
    private INotificacaoPendenteRepository notificacoes;
    private AutorizacaoBarbeiroService autorizacao;
    private BloqueioDataService service;

    @BeforeEach
    void preparar() {
        agendamentos = mock(IAgendamentoRepository.class);
        barbeiros = mock(IBarbeiroRepository.class);
        bloqueios = mock(IBloqueioDataRepository.class);
        notificacoes = mock(INotificacaoPendenteRepository.class);
        autorizacao = mock(AutorizacaoBarbeiroService.class);

        service = new BloqueioDataService(
                agendamentos,
                barbeiros,
                bloqueios,
                notificacoes,
                autorizacao
        );
    }

    @Test
    void consultarAfetadosValidaParametrosEData() {
        assertThrows(
                OperacaoAdministrativaException.class,
                () -> service.consultarAfetados(null, LocalDate.now())
        );

        assertThrows(
                OperacaoAdministrativaException.class,
                () -> service.consultarAfetados(
                        1L,
                        LocalDate.now().minusDays(1)
                )
        );

        when(barbeiros.existsById(1L)).thenReturn(false);

        assertThrows(
                OperacaoAdministrativaException.class,
                () -> service.consultarAfetados(
                        1L,
                        LocalDate.now().plusDays(1)
                )
        );
    }

    @Test
    void confirmarBloqueioSemReservasCriaBloqueio() {
        LocalDate data = LocalDate.now().plusDays(2);
        Barbeiro barbeiro = Barbeiro.builder()
                .id(1L)
                .nome("Zalura")
                .build();

        when(barbeiros.buscarParaAgendar(1L))
                .thenReturn(Optional.of(barbeiro));
        when(bloqueios.existsByBarbeiroIdAndData(1L, data))
                .thenReturn(false);
        when(agendamentos.buscarAfetadosPorBloqueio(
                eq(1L), any(), any(), any()
        )).thenReturn(List.of());

        int cancelados = service.confirmarBloqueio(
                1L,
                data,
                "Folga",
                List.of()
        );

        assertEquals(0, cancelados);
        verify(bloqueios).save(argThat(b ->
                b.getBarbeiro() == barbeiro
                        && b.getData().equals(data)
                        && b.getMotivo().equals("Folga")
        ));
        verify(agendamentos).flush();
        verifyNoInteractions(notificacoes);
    }

    @Test
    void confirmarRejeitaQuandoListaMudou() {
        LocalDate data = LocalDate.now().plusDays(2);
        Barbeiro barbeiro = Barbeiro.builder()
                .id(1L)
                .whatsappPhoneNumberId("phone")
                .build();

        when(barbeiros.buscarParaAgendar(1L))
                .thenReturn(Optional.of(barbeiro));
        when(agendamentos.buscarAfetadosPorBloqueio(
                eq(1L), any(), any(), any()
        )).thenReturn(List.of());

        assertThrows(
                OperacaoAdministrativaException.class,
                () -> service.confirmarBloqueio(
                        1L,
                        data,
                        "Folga",
                        List.of(99L)
                )
        );

        verify(bloqueios, never()).save(any(BloqueioData.class));
    }

    @Test
    void liberarDiaExigeAutorizacaoERetornaSeRemoveuAlgo() {
        LocalDate data = LocalDate.now().plusDays(1);
        when(barbeiros.buscarParaAgendar(1L))
                .thenReturn(Optional.of(Barbeiro.builder().id(1L).build()));
        when(autorizacao.podeAdministrar(1L, "5588999999999"))
                .thenReturn(true);
        when(bloqueios.deleteByBarbeiroIdAndData(1L, data))
                .thenReturn(1L);

        assertTrue(service.liberarDia(1L, "5588999999999", data));

        when(autorizacao.podeAdministrar(1L, "5588000000000"))
                .thenReturn(false);

        assertThrows(
                OperacaoAdministrativaException.class,
                () -> service.liberarDia(
                        1L,
                        "5588000000000",
                        data
                )
        );
    }
}

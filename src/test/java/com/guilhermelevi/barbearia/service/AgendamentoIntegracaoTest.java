package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.domain.enums.StatusAgendamentoEnum;
import com.guilhermelevi.barbearia.domain.exception.CancelamentoNaoPermitidoException;
import com.guilhermelevi.barbearia.domain.exception.ServicoIndisponivelException;
import com.guilhermelevi.barbearia.repositories.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgendamentoIntegracaoTest {

    private IAgendamentoRepository agendamentos;
    private IBarbeiroRepository barbeiros;
    private NotificacaoService notificacao;
    private IExpedienteSemanalRepository expedientes;
    private IBloqueioDataRepository bloqueios;
    private IPeriodoExpedienteRepository periodos;
    private AutorizacaoBarbeiroService autorizacao;
    private IServicoRepository servicos;
    private EntityManager entityManager;
    private AgendamentoService service;

    private Barbeiro barbeiro;
    private Cliente cliente;
    private Servico servico;

    @BeforeEach
    void preparar() {
        agendamentos = mock(IAgendamentoRepository.class);
        barbeiros = mock(IBarbeiroRepository.class);
        notificacao = mock(NotificacaoService.class);
        expedientes = mock(IExpedienteSemanalRepository.class);
        bloqueios = mock(IBloqueioDataRepository.class);
        periodos = mock(IPeriodoExpedienteRepository.class);
        autorizacao = mock(AutorizacaoBarbeiroService.class);
        servicos = mock(IServicoRepository.class);
        entityManager = mock(EntityManager.class);

        service = new AgendamentoService(
                agendamentos,
                barbeiros,
                notificacao,
                expedientes,
                bloqueios,
                periodos,
                autorizacao,
                servicos,
                entityManager
        );

        barbeiro = Barbeiro.builder().id(1L).build();
        cliente = Cliente.builder().id(2L).build();
        servico = Servico.builder()
                .id(3L)
                .nome("Corte")
                .preco(new BigDecimal("30.00"))
                .duracaoMinutos(30)
                .barbeiro(barbeiro)
                .ativo(true)
                .build();
    }

    @Test
    void listarProximosConsultaSomenteConfirmadosDoCliente() {
        when(agendamentos.buscarProximosDoCliente(
                eq(2L),
                eq(1L),
                any(LocalDateTime.class),
                eq(StatusAgendamentoEnum.CONFIRMADO)
        )).thenReturn(List.of());

        assertTrue(service.listarProximos(cliente, barbeiro).isEmpty());

        verify(agendamentos).buscarProximosDoCliente(
                eq(2L),
                eq(1L),
                any(LocalDateTime.class),
                eq(StatusAgendamentoEnum.CONFIRMADO)
        );
    }

    @Test
    void cancelarMudaStatusEPersisteENotifica() {
        Agendamento agendamento = Agendamento.builder()
                .id(10L)
                .cliente(cliente)
                .barbeiro(barbeiro)
                .servico(servico)
                .inicio(LocalDateTime.now().plusDays(1))
                .duracaoMinutos(30)
                .status(StatusAgendamentoEnum.CONFIRMADO)
                .build();

        when(barbeiros.buscarParaAgendar(1L))
                .thenReturn(Optional.of(barbeiro));
        when(agendamentos.buscarDoClienteNaBarbearia(10L, 2L, 1L))
                .thenReturn(Optional.of(agendamento));
        when(agendamentos.saveAndFlush(agendamento))
                .thenReturn(agendamento);

        assertTrue(service.cancelar(10L, cliente, barbeiro));

        assertEquals(StatusAgendamentoEnum.CANCELADO, agendamento.getStatus());
        verify(agendamentos).saveAndFlush(agendamento);
        verify(notificacao).agendamentoCancelado(agendamento);
    }

    @Test
    void cancelarReservaJaCanceladaEhIdempotente() {
        Agendamento agendamento = Agendamento.builder()
                .id(10L)
                .cliente(cliente)
                .barbeiro(barbeiro)
                .servico(servico)
                .inicio(LocalDateTime.now().plusDays(1))
                .duracaoMinutos(30)
                .status(StatusAgendamentoEnum.CANCELADO)
                .build();

        when(barbeiros.buscarParaAgendar(1L))
                .thenReturn(Optional.of(barbeiro));
        when(agendamentos.buscarDoClienteNaBarbearia(10L, 2L, 1L))
                .thenReturn(Optional.of(agendamento));

        assertFalse(service.cancelar(10L, cliente, barbeiro));
        verify(agendamentos, never()).saveAndFlush(any());
        verifyNoInteractions(notificacao);
    }

    @Test
    void buscarParaCancelarRejeitaHorarioPassado() {
        Agendamento passado = Agendamento.builder()
                .id(10L)
                .cliente(cliente)
                .barbeiro(barbeiro)
                .servico(servico)
                .inicio(LocalDateTime.now().minusMinutes(1))
                .duracaoMinutos(30)
                .status(StatusAgendamentoEnum.CONFIRMADO)
                .build();

        when(agendamentos.buscarDoClienteNaBarbearia(10L, 2L, 1L))
                .thenReturn(Optional.of(passado));

        assertThrows(
                CancelamentoNaoPermitidoException.class,
                () -> service.buscarParaCancelar(10L, cliente, barbeiro)
        );
    }

    @Test
    void agendarRejeitaServicoSemIdentificador() {
        when(barbeiros.buscarParaAgendar(1L))
                .thenReturn(Optional.of(barbeiro));

        assertThrows(
                ServicoIndisponivelException.class,
                () -> service.agendar(
                        cliente,
                        barbeiro,
                        Servico.builder().build(),
                        LocalDateTime.now().plusDays(1),
                        BigDecimal.TEN,
                        30
                )
        );
    }

    @Test
    void agendaDoDiaExigePermissaoAdministrativa() {
        when(autorizacao.podeAdministrar(1L, "5588999999999"))
                .thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.listarAgendaDoDia(
                        1L,
                        "5588999999999",
                        LocalDate.now().plusDays(1)
                )
        );
    }
}

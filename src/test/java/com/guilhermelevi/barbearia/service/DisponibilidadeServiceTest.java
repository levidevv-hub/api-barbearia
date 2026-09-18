package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.domain.enums.StatusAgendamentoEnum;
import com.guilhermelevi.barbearia.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DisponibilidadeServiceTest {

    private IAgendamentoRepository agendamentos;
    private IExpedienteSemanalRepository expedientes;
    private IBloqueioDataRepository bloqueios;
    private IPeriodoExpedienteRepository periodos;
    private DisponibilidadeService service;
    private Barbeiro barbeiro;
    private Servico servico;

    @BeforeEach
    void preparar() {
        agendamentos = mock(IAgendamentoRepository.class);
        expedientes = mock(IExpedienteSemanalRepository.class);
        bloqueios = mock(IBloqueioDataRepository.class);
        periodos = mock(IPeriodoExpedienteRepository.class);
        service = new DisponibilidadeService(
                agendamentos, expedientes, bloqueios, periodos
        );
        barbeiro = Barbeiro.builder().id(1L).build();
        servico = Servico.builder().duracaoMinutos(30).build();
    }

    @Test
    void dataPassadaOuServicoInvalidoNaoTemHorario() {
        assertTrue(service.buscarHorarios(
                barbeiro, servico, LocalDate.now().minusDays(1)
        ).isEmpty());

        servico.setDuracaoMinutos(0);
        assertTrue(service.buscarHorarios(
                barbeiro, servico, LocalDate.now().plusDays(1)
        ).isEmpty());
    }

    @Test
    void bloqueioOuDiaFechadoNaoTemHorario() {
        LocalDate data = LocalDate.now().plusDays(2);

        when(bloqueios.existsByBarbeiroIdAndData(1L, data))
                .thenReturn(true);
        assertTrue(service.buscarHorarios(barbeiro, servico, data).isEmpty());

        reset(bloqueios);
        when(expedientes.findByBarbeiroIdAndDiaSemana(
                1L, data.getDayOfWeek()
        )).thenReturn(Optional.of(
                ExpedienteSemanal.builder()
                        .id(3L)
                        .diaSemana(data.getDayOfWeek())
                        .aberto(false)
                        .build()
        ));

        assertTrue(service.buscarHorarios(barbeiro, servico, data).isEmpty());
    }

    @Test
    void geraSlotsEExcluiConflitos() {
        LocalDate data = LocalDate.now().plusDays(3);
        ExpedienteSemanal expediente = ExpedienteSemanal.builder()
                .id(3L)
                .diaSemana(data.getDayOfWeek())
                .aberto(true)
                .inicio(LocalTime.of(8, 0))
                .fim(LocalTime.of(10, 0))
                .build();

        when(expedientes.findByBarbeiroIdAndDiaSemana(
                1L, data.getDayOfWeek()
        )).thenReturn(Optional.of(expediente));

        when(periodos.findByExpedienteSemanalIdOrderByInicioAsc(3L))
                .thenReturn(List.of(
                        PeriodoExpediente.builder()
                                .inicio(LocalTime.of(8, 0))
                                .fim(LocalTime.of(10, 0))
                                .build()
                ));

        Agendamento existente = Agendamento.builder()
                .inicio(data.atTime(8, 30))
                .duracaoMinutos(30)
                .status(StatusAgendamentoEnum.CONFIRMADO)
                .build();

        when(agendamentos.buscarReservasAntesDe(
                eq(1L),
                eq(data.atTime(10, 0)),
                eq(StatusAgendamentoEnum.CONFIRMADO)
        )).thenReturn(List.of(existente));

        List<LocalTime> livres = service.buscarHorarios(
                barbeiro, servico, data
        );

        assertTrue(livres.contains(LocalTime.of(8, 0)));
        assertFalse(livres.contains(LocalTime.of(8, 30)));
        assertTrue(livres.contains(LocalTime.of(9, 0)));
        assertTrue(livres.contains(LocalTime.of(9, 30)));
    }

    @Test
    void periodosSobrepostosInvalidamAgenda() {
        LocalDate data = LocalDate.now().plusDays(3);
        ExpedienteSemanal expediente = ExpedienteSemanal.builder()
                .id(3L)
                .diaSemana(data.getDayOfWeek())
                .aberto(true)
                .inicio(LocalTime.of(8, 0))
                .fim(LocalTime.of(18, 0))
                .build();

        when(expedientes.findByBarbeiroIdAndDiaSemana(
                1L, data.getDayOfWeek()
        )).thenReturn(Optional.of(expediente));
        when(periodos.findByExpedienteSemanalIdOrderByInicioAsc(3L))
                .thenReturn(List.of(
                        PeriodoExpediente.builder()
                                .inicio(LocalTime.of(8, 0))
                                .fim(LocalTime.of(12, 0))
                                .build(),
                        PeriodoExpediente.builder()
                                .inicio(LocalTime.of(11, 0))
                                .fim(LocalTime.of(18, 0))
                                .build()
                ));

        assertTrue(service.buscarHorarios(barbeiro, servico, data).isEmpty());
        verifyNoInteractions(agendamentos);
    }
}

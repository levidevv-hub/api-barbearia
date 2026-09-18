package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.domain.enums.StatusAgendamentoEnum;
import com.guilhermelevi.barbearia.repositories.IAgendamentoRepository;
import com.guilhermelevi.barbearia.repositories.IBloqueioDataRepository;
import com.guilhermelevi.barbearia.repositories.IExpedienteSemanalRepository;
import com.guilhermelevi.barbearia.repositories.IPeriodoExpedienteRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class DisponibilidadeService {

    private final IAgendamentoRepository repository;
    private final IExpedienteSemanalRepository expedienteRepository;
    private final IBloqueioDataRepository bloqueioRepository;
    private final IPeriodoExpedienteRepository periodoRepository;

    public List<LocalTime> buscarHorarios(
            Barbeiro barbeiro,
            Servico servico,
            LocalDate data
    ) {
        LocalDateTime agora = LocalDateTime.now();

        if (data.isBefore(agora.toLocalDate())
                || servico.getDuracaoMinutos() == null
                || servico.getDuracaoMinutos() <= 0) {
            return List.of();
        }

        if (bloqueioRepository.existsByBarbeiroIdAndData(
                barbeiro.getId(), data
        )) {
            return List.of();
        }

        ExpedienteSemanal expediente = expedienteRepository
                .findByBarbeiroIdAndDiaSemana(
                        barbeiro.getId(),
                        data.getDayOfWeek()
                )
                .orElse(null);

        if (expediente == null || !expediente.isAberto()) {
            return List.of();
        }

        List<PeriodoExpediente> periodos = periodoRepository
                .findByExpedienteSemanalIdOrderByInicioAsc(
                        expediente.getId()
                );

        if (periodos.isEmpty()) {
            return List.of();
        }

        // Valida os períodos e impede sobreposição.
        LocalTime fimAnterior = null;

        for (PeriodoExpediente periodo : periodos) {
            if (periodo.getInicio() == null
                    || periodo.getFim() == null
                    || !periodo.getFim().isAfter(periodo.getInicio())) {
                return List.of();
            }

            if (fimAnterior != null
                    && periodo.getInicio().isBefore(fimAnterior)) {
                return List.of();
            }

            fimAnterior = periodo.getFim();
        }

        LocalDateTime fimExpediente = data.atTime(
                periodos.get(periodos.size() - 1).getFim()
        );

        List<Agendamento> existentes = repository.buscarReservasAntesDe(
                barbeiro.getId(),
                fimExpediente,
                StatusAgendamentoEnum.CONFIRMADO
        );

        List<LocalTime> disponiveis = new ArrayList<>();

        for (PeriodoExpediente periodo : periodos) {
            LocalDateTime fimPeriodo = data.atTime(periodo.getFim());

            for (LocalDateTime inicio = data.atTime(periodo.getInicio());
                 !inicio.plusMinutes(servico.getDuracaoMinutos())
                         .isAfter(fimPeriodo);
                 inicio = inicio.plusMinutes(30)) {

                if (!inicio.isAfter(agora)) {
                    continue;
                }

                PeriodoAgendamento novo = new PeriodoAgendamento(
                        inicio,
                        inicio.plusMinutes(servico.getDuracaoMinutos())
                );

                boolean conflito = existentes.stream()
                        .anyMatch(agendamento -> novo.sobrepoe(
                                new PeriodoAgendamento(
                                        agendamento.getInicio(),
                                        agendamento.calcularFim()
                                )
                        ));

                if (!conflito) {
                    disponiveis.add(inicio.toLocalTime());
                }
            }
        }

        return disponiveis;
    }
}
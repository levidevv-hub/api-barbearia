package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.domain.enums.StatusAgendamentoEnum;
import com.guilhermelevi.barbearia.domain.exception.HorarioIndisponivelException;
import com.guilhermelevi.barbearia.domain.exception.CancelamentoNaoPermitidoException;
import com.guilhermelevi.barbearia.domain.exception.ServicoIndisponivelException;
import com.guilhermelevi.barbearia.repositories.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import com.guilhermelevi.barbearia.domain.exception.ServicoAlteradoException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@AllArgsConstructor
public class AgendamentoService {

    private final IAgendamentoRepository repository;
    private final IBarbeiroRepository barbeiroRepository;
    private final NotificacaoService notificacaoService;
    private final IExpedienteSemanalRepository expedienteRepository;
    private final IBloqueioDataRepository bloqueioRepository;
    private final IPeriodoExpedienteRepository periodoRepository;
    private final AutorizacaoBarbeiroService autorizacaoService;
    private final IServicoRepository servicoRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<Agendamento> listarProximos(Cliente cliente, Barbeiro barbeiro) {
        return repository.buscarProximosDoCliente(
                cliente.getId(), barbeiro.getId(), LocalDateTime.now(),
                StatusAgendamentoEnum.CONFIRMADO);
    }

    @Transactional(
            readOnly = true,
            noRollbackFor = CancelamentoNaoPermitidoException.class
    )
    public Agendamento buscarParaCancelar(Long agendamentoId, Cliente cliente, Barbeiro barbeiro) {
        Agendamento agendamento = buscarReservaDoCliente(agendamentoId, cliente, barbeiro);
        validarCancelamento(agendamento);
        return agendamento;
    }

    @Transactional(
            isolation = Isolation.READ_COMMITTED,
            noRollbackFor = CancelamentoNaoPermitidoException.class
    )
    public boolean cancelar(Long agendamentoId, Cliente cliente, Barbeiro barbeiro) {
        // A mesma trava de agendar: confirmações e cancelamentos são serializados por barbeiro.
        barbeiroRepository.buscarParaAgendar(barbeiro.getId())
                .orElseThrow(() -> new CancelamentoNaoPermitidoException("Barbearia não encontrada."));

        Agendamento agendamento = buscarReservaDoCliente(agendamentoId, cliente, barbeiro);
        if (agendamento.getStatus() == StatusAgendamentoEnum.CANCELADO) {
            return false; // Repetir a confirmação não cancela outra reserva.
        }
        validarCancelamento(agendamento);
        agendamento.setStatus(StatusAgendamentoEnum.CANCELADO);
        repository.saveAndFlush(agendamento);
        notificacaoService.agendamentoCancelado(agendamento);
        return true;
    }

    private Agendamento buscarReservaDoCliente(Long agendamentoId, Cliente cliente, Barbeiro barbeiro) {
        return repository.buscarDoClienteNaBarbearia(agendamentoId, cliente.getId(), barbeiro.getId())
                .orElseThrow(() -> new CancelamentoNaoPermitidoException(
                        "Não encontrei esse agendamento entre suas reservas nesta barbearia."));
    }

    private void validarCancelamento(Agendamento agendamento) {
        if (agendamento.getStatus() == StatusAgendamentoEnum.CANCELADO) {
            throw new CancelamentoNaoPermitidoException("Esse agendamento já foi cancelado.");
        }
        if (agendamento.getStatus() != null
                && agendamento.getStatus() != StatusAgendamentoEnum.CONFIRMADO) {
            throw new CancelamentoNaoPermitidoException("Esse agendamento não pode ser cancelado.");
        }
        if (!agendamento.getInicio().isAfter(LocalDateTime.now())) {
            throw new CancelamentoNaoPermitidoException(
                    "Esse atendimento já começou ou passou. Entre em contato com a barbearia.");
        }
    }

    @Transactional(
            isolation = Isolation.READ_COMMITTED,
            noRollbackFor = {
                    HorarioIndisponivelException.class,
                    ServicoIndisponivelException.class,
                    ServicoAlteradoException.class
            }
    )
    public Agendamento agendar(
            Cliente cliente,
            Barbeiro barbeiro,
            Servico servico,
            LocalDateTime inicio,
            BigDecimal precoApresentado,
            Integer duracaoApresentada
    ) {
        Barbeiro barbeiroBloqueado = barbeiroRepository
                .buscarParaAgendar(barbeiro.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Barbeiro não encontrado."
                        )
                );

        if (servico == null || servico.getId() == null) {
            throw new ServicoIndisponivelException(
                    "Selecione novamente o serviço para agendar."
            );
        }

        Servico servicoAtual = servicoRepository
                .findByIdAndBarbeiroId(
                        servico.getId(),
                        barbeiroBloqueado.getId()
                )
                .orElseThrow(() ->
                        new ServicoIndisponivelException(
                                "Esse serviço não está mais disponível."
                        )
                );

        // Recarrega os dados do banco após obter a trava.
        entityManager.refresh(servicoAtual);

        if (!servicoAtual.isAtivo()) {
            throw new ServicoIndisponivelException(
                    "Esse serviço não está mais disponível. Escolha outro serviço para agendar."
            );
        }

        validarServico(barbeiroBloqueado, servicoAtual);

        if (precoApresentado == null
                || duracaoApresentada == null
                || servicoAtual.getPreco() == null
                || precoApresentado.compareTo(
                servicoAtual.getPreco()
        ) != 0
                || !duracaoApresentada.equals(
                servicoAtual.getDuracaoMinutos()
        )) {

            throw new ServicoAlteradoException(
                    "Precisamos atualizar sua confirmação: o preço ou a duração do serviço mudou, ou a confirmação é antiga. Selecione novamente o serviço e o horário para conferir as condições atuais."
            );
        }

        LocalDateTime fim = inicio.plusMinutes(
                servicoAtual.getDuracaoMinutos()
        );

        validarExpediente(barbeiroBloqueado, inicio, fim);

        verificarConflito(
                barbeiroBloqueado,
                new PeriodoAgendamento(inicio, fim)
        );

        Agendamento agendamento = new Agendamento(
                cliente,
                barbeiroBloqueado,
                servicoAtual,
                inicio
        );

        agendamento.setStatus(StatusAgendamentoEnum.CONFIRMADO);

        Agendamento salvo = repository.saveAndFlush(agendamento);

        notificacaoService.novoAgendamento(salvo);

        return salvo;
    }

    private void validarServico(Barbeiro barbeiro, Servico servico) {
        if (servico == null || servico.getDuracaoMinutos() == null
                || servico.getDuracaoMinutos() <= 0) {
            throw new IllegalArgumentException("O serviço deve ter uma duração positiva.");
        }
        if (servico.getBarbeiro() == null
                || !Objects.equals(servico.getBarbeiro().getId(), barbeiro.getId())) {
            throw new IllegalArgumentException("O serviço não pertence a este barbeiro.");
        }
    }

    private void validarExpediente(
            Barbeiro barbeiro,
            LocalDateTime inicio,
            LocalDateTime fim
    ) {
        if (!inicio.isAfter(LocalDateTime.now())) {
            throw new HorarioIndisponivelException(
                    "Esse horário já passou. Escolha outro horário."
            );
        }

        if (bloqueioRepository.existsByBarbeiroIdAndData(
                barbeiro.getId(), inicio.toLocalDate()
        )) {
            throw new HorarioIndisponivelException(
                    "A barbearia não atenderá nessa data. Escolha outro dia."
            );
        }

        ExpedienteSemanal expediente = expedienteRepository
                .findByBarbeiroIdAndDiaSemana(
                        barbeiro.getId(),
                        inicio.getDayOfWeek()
                )
                .orElseThrow(() -> new HorarioIndisponivelException(
                        "Não há expediente cadastrado para esse dia."
                ));

        if (!expediente.isAberto()) {
            throw new HorarioIndisponivelException(
                    "A barbearia está fechada nesse dia. Escolha outra data."
            );
        }

        List<PeriodoExpediente> periodos = periodoRepository
                .findByExpedienteSemanalIdOrderByInicioAsc(
                        expediente.getId()
                );

        java.time.LocalTime fimAnterior = null;

        for (PeriodoExpediente periodo : periodos) {
            if (periodo.getInicio() == null
                    || periodo.getFim() == null
                    || !periodo.getFim().isAfter(periodo.getInicio())) {
                throw new HorarioIndisponivelException(
                        "O expediente desse dia está inválido. "
                                + "Entre em contato com a barbearia."
                );
            }

            if (fimAnterior != null
                    && periodo.getInicio().isBefore(fimAnterior)) {
                throw new HorarioIndisponivelException(
                        "Existem períodos sobrepostos nesse dia. "
                                + "Entre em contato com a barbearia."
                );
            }

            fimAnterior = periodo.getFim();
        }

        boolean cabeEmUmPeriodo = periodos.stream()
                .anyMatch(periodo -> {
                    LocalDateTime inicioPeriodo = inicio.toLocalDate()
                            .atTime(periodo.getInicio());

                    LocalDateTime fimPeriodo = inicio.toLocalDate()
                            .atTime(periodo.getFim());

                    return !inicio.isBefore(inicioPeriodo)
                            && !fim.isAfter(fimPeriodo);
                });

        if (!cabeEmUmPeriodo) {
            throw new HorarioIndisponivelException(
                    "Esse atendimento não cabe em um período disponível. "
                            + "Escolha outro horário."
            );
        }
    }

    private void verificarConflito(Barbeiro barbeiro, PeriodoAgendamento periodo) {
        List<Agendamento> existentes = repository.buscarReservasAntesDe(
                barbeiro.getId(), periodo.fim(), StatusAgendamentoEnum.CONFIRMADO);

        boolean conflito = existentes.stream().anyMatch(a -> periodo.sobrepoe(
                new PeriodoAgendamento(a.getInicio(), a.calcularFim())));

        if (conflito) {
            throw new HorarioIndisponivelException(
                    "Esse horário acabou de ser reservado. Escolha outro horário.");
        }
    }

    @Transactional(readOnly = true)
    public List<Agendamento> listarAgendaDoDia(
            Long barbeiroId,
            String numeroAdministrador,
            java.time.LocalDate data
    ) {
        if (barbeiroId == null || data == null) {
            throw new IllegalArgumentException(
                    "Informe o barbeiro e a data da consulta."
            );
        }

        if (!autorizacaoService.podeAdministrar(
                barbeiroId,
                numeroAdministrador
        )) {
            throw new IllegalArgumentException(
                    "Esse número não tem permissão para consultar esta agenda."
            );
        }

        return repository.buscarAfetadosPorBloqueio(
                barbeiroId,
                data.atStartOfDay(),
                data.plusDays(1).atStartOfDay(),
                StatusAgendamentoEnum.CONFIRMADO
        );
    }
}
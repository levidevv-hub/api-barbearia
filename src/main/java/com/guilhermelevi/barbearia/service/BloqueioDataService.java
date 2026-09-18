package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Agendamento;
import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.domain.BloqueioData;
import com.guilhermelevi.barbearia.domain.NotificacaoPendente;
import com.guilhermelevi.barbearia.domain.enums.StatusAgendamentoEnum;
import com.guilhermelevi.barbearia.domain.exception.OperacaoAdministrativaException;
import com.guilhermelevi.barbearia.repositories.IAgendamentoRepository;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import com.guilhermelevi.barbearia.repositories.IBloqueioDataRepository;
import com.guilhermelevi.barbearia.repositories.INotificacaoPendenteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BloqueioDataService {

    private final IAgendamentoRepository agendamentoRepository;
    private final IBarbeiroRepository barbeiroRepository;
    private final IBloqueioDataRepository bloqueioRepository;
    private final INotificacaoPendenteRepository notificacaoRepository;
    private final AutorizacaoBarbeiroService autorizacaoService;

    @Transactional(
            readOnly = true,
            noRollbackFor = OperacaoAdministrativaException.class
    )
    public List<ReservaAfetada> consultarAfetados(
            Long barbeiroId,
            LocalDate data
    ) {
        if (barbeiroId == null || data == null) {
            throw new OperacaoAdministrativaException(
                    "Informe o barbeiro e a data do bloqueio."
            );
        }

        if (data.isBefore(LocalDate.now())) {
            throw new OperacaoAdministrativaException(
                    "Não é possível bloquear uma data passada."
            );
        }

        if (!barbeiroRepository.existsById(barbeiroId)) {
            throw new OperacaoAdministrativaException(
                    "Barbeiro não encontrado."
            );
        }

        LocalDateTime inicioDia = data.atStartOfDay();
        LocalDateTime fimDia = data.plusDays(1).atStartOfDay();

        LocalDateTime agora = LocalDateTime.now();

        return agendamentoRepository.buscarAfetadosPorBloqueio(
                        barbeiroId,
                        inicioDia,
                        fimDia,
                        StatusAgendamentoEnum.CONFIRMADO
                )
                .stream()
                .filter(agendamento ->
                        agendamento.getInicio().isAfter(agora))
                .map(agendamento -> new ReservaAfetada(
                        agendamento.getId(),
                        agendamento.getCliente().getNomeCompleto(),
                        agendamento.getServico().getNome(),
                        agendamento.getInicio()
                ))
                .toList();
    }

    public record ReservaAfetada(
            Long agendamentoId,
            String cliente,
            String servico,
            LocalDateTime inicio
    ) {
    }

    @Transactional(
            isolation = Isolation.READ_COMMITTED,
            noRollbackFor = OperacaoAdministrativaException.class
    )
    public int confirmarBloqueio(
            Long barbeiroId,
            LocalDate data,
            String motivo,
            List<Long> idsConfirmados
    ) {
        if (barbeiroId == null || data == null || idsConfirmados == null) {
            throw new OperacaoAdministrativaException(
                    "Informe o barbeiro, a data e as reservas confirmadas."
            );
        }

        if (idsConfirmados.stream().anyMatch(id -> id == null)) {
            throw new OperacaoAdministrativaException(
                    "A lista de reservas contém um identificador inválido."
            );
        }

        if (motivo == null || motivo.isBlank() || motivo.length() > 255) {
            throw new OperacaoAdministrativaException(
                    "Informe um motivo com até 255 caracteres."
            );
        }

        // Usa a mesma trava que protege a criação de agendamentos.
        Barbeiro barbeiro = barbeiroRepository
                .buscarParaAgendar(barbeiroId)
                .orElseThrow(() -> new OperacaoAdministrativaException(
                        "Barbeiro não encontrado."
                ));

        LocalDateTime agora = LocalDateTime.now();

        if (data.isBefore(agora.toLocalDate())) {
            throw new OperacaoAdministrativaException(
                    "Não é possível bloquear uma data passada."
            );
        }

        if (bloqueioRepository.existsByBarbeiroIdAndData(barbeiroId, data)) {
            throw new OperacaoAdministrativaException(
                    "Essa data já está bloqueada."
            );
        }

        List<Agendamento> afetados = agendamentoRepository
                .buscarAfetadosPorBloqueio(
                        barbeiroId,
                        data.atStartOfDay(),
                        data.plusDays(1).atStartOfDay(),
                        StatusAgendamentoEnum.CONFIRMADO
                )
                .stream()
                .filter(agendamento ->
                        agendamento.getInicio().isAfter(agora))
                .toList();

        Set<Long> idsAtuais = afetados.stream()
                .map(Agendamento::getId)
                .collect(Collectors.toSet());

        Set<Long> idsDaPrevia = new HashSet<>(idsConfirmados);

        if (!idsAtuais.equals(idsDaPrevia)) {
            throw new OperacaoAdministrativaException(
                    "As reservas mudaram desde a prévia. "
                            + "Consulte novamente e confirme os clientes afetados."
            );
        }

        // Confere os dados antes de começar os cancelamentos.
        for (Agendamento agendamento : afetados) {
            String telefone = agendamento.getCliente().getNumeroTelefone();

            if (telefone == null || telefone.isBlank()) {
                throw new OperacaoAdministrativaException(
                        "O cliente do agendamento " + agendamento.getId()
                                + " está sem telefone. Corrija antes de bloquear."
                );
            }
        }

        if (!afetados.isEmpty()
                && (barbeiro.getWhatsappPhoneNumberId() == null
                || barbeiro.getWhatsappPhoneNumberId().isBlank())) {
            throw new OperacaoAdministrativaException(
                    "O barbeiro está sem configuração de envio do WhatsApp."
            );
        }

        BloqueioData bloqueio = BloqueioData.builder()
                .barbeiro(barbeiro)
                .data(data)
                .motivo(motivo.strip())
                .build();

        bloqueioRepository.save(bloqueio);

        DateTimeFormatter formatoData =
                DateTimeFormatter.ofPattern("dd/MM/yyyy");

        DateTimeFormatter formatoHora =
                DateTimeFormatter.ofPattern("HH:mm");

        for (Agendamento agendamento : afetados) {
            agendamento.setStatus(StatusAgendamentoEnum.CANCELADO);

            String mensagem = """
                Olá, %s.

                A barbearia precisou cancelar seu agendamento:

                Serviço: %s
                Barbeiro: %s
                Data: %s
                Horário: %s

                Pedimos desculpas pelo imprevisto.
                Envie oi para escolher uma nova data.
                """.formatted(
                    agendamento.getCliente().getNomeCompleto(),
                    agendamento.getServico().getNome(),
                    barbeiro.getNome(),
                    agendamento.getInicio().format(formatoData),
                    agendamento.getInicio().format(formatoHora)
            );

            notificacaoRepository.save(
                    new NotificacaoPendente(agendamento, mensagem)
            );
        }

        // As entidades consultadas estão gerenciadas pelo JPA.
        // O flush grava também as alterações de status.
        agendamentoRepository.flush();

        return afetados.size();
    }

    @Transactional(
            isolation = Isolation.READ_COMMITTED,
            noRollbackFor = OperacaoAdministrativaException.class
    )
    public boolean liberarDia(
            Long barbeiroId,
            String numeroAdministrador,
            LocalDate data
    ) {
        if (barbeiroId == null || data == null) {
            throw new OperacaoAdministrativaException(
                    "Informe o barbeiro e a data."
            );
        }

        barbeiroRepository.buscarParaAgendar(barbeiroId)
                .orElseThrow(() -> new OperacaoAdministrativaException(
                        "Barbeiro não encontrado."
                ));

        if (!autorizacaoService.podeAdministrar(
                barbeiroId,
                numeroAdministrador
        )) {
            throw new OperacaoAdministrativaException(
                    "Esse número não tem permissão para administrar esta agenda."
            );
        }

        if (data.isBefore(LocalDate.now())) {
            throw new OperacaoAdministrativaException(
                    "Informe uma data de hoje em diante."
            );
        }

        long removidos = bloqueioRepository.deleteByBarbeiroIdAndData(
                barbeiroId,
                data
        );

        return removidos > 0;
    }
}
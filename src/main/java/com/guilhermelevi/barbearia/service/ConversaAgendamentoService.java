package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.domain.enums.EtapaConversaEnum;
import com.guilhermelevi.barbearia.domain.exception.CancelamentoNaoPermitidoException;
import com.guilhermelevi.barbearia.domain.exception.HorarioIndisponivelException;
import com.guilhermelevi.barbearia.domain.exception.ServicoAlteradoException;
import com.guilhermelevi.barbearia.domain.exception.ServicoIndisponivelException;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.WhatsAppClient;
import com.guilhermelevi.barbearia.repositories.IServicoRepository;
import com.guilhermelevi.barbearia.repositories.ISessaoConversaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversaAgendamentoService {

    private final IServicoRepository servicoRepository;
    private final ISessaoConversaRepository sessaoRepository;
    private final DisponibilidadeService disponibilidadeService;
    private final AgendamentoService agendamentoService;
    private final WhatsAppClient whatsapp;

    public void iniciar(Barbeiro barbeiro, Cliente cliente, String telefone) {

        SessaoConversa sessao =
                sessaoRepository
                        .findByNumeroClienteAndBarbeiroId(
                                telefone,
                                barbeiro.getId()
                        )
                        .orElseGet(SessaoConversa::new);

        sessao.setNumeroCliente(telefone);
        sessao.setBarbeiro(barbeiro);
        sessao.limpar();
        sessao.atualizarInteracao();

        sessaoRepository.save(sessao);

        whatsapp.enviarMenuPrincipal(
                barbeiro.getWhatsappPhoneNumberId(),
                telefone
        );
    }

    public void processarInteracao(
            String id,
            Barbeiro barbeiro,
            Cliente cliente,
            String telefone
    ) {
        if ("CONSULTAR".equals(id)) {
            List<Agendamento> agendamentos =
                    agendamentoService.listarProximos(cliente, barbeiro);

            whatsapp.enviarMeusHorarios(
                    barbeiro.getWhatsappPhoneNumberId(),
                    cliente.getNumeroTelefone(),
                    agendamentos
            );
            return;
        }

        if (processarCancelamento(id, cliente, barbeiro)) {
            return;
        }

        SessaoConversa sessao = sessaoRepository
                .findByNumeroClienteAndBarbeiroId(
                        telefone,
                        barbeiro.getId()
                )
                .orElseThrow();

        processarOpcao(id, sessao, cliente, barbeiro);
    }

    private boolean processarCancelamento(String id, Cliente cliente, Barbeiro barbeiro) {
        String phoneNumberId = barbeiro.getWhatsappPhoneNumberId();
        String numero = cliente.getNumeroTelefone();
        if ("CANCELAR".equals(id)) {
            whatsapp.enviarOpcoesCancelamento(phoneNumberId, numero,
                    agendamentoService.listarProximos(cliente, barbeiro));
            return true;
        }

        String prefixo;
        if (id.startsWith("ESCOLHER_CANCELAMENTO_")) {
            prefixo = "ESCOLHER_CANCELAMENTO_";
        } else if (id.startsWith("CONFIRMAR_CANCELAMENTO_")) {
            prefixo = "CONFIRMAR_CANCELAMENTO_";
        } else if (id.startsWith("MANTER_AGENDAMENTO_")) {
            prefixo = "MANTER_AGENDAMENTO_";
        } else {
            return false;
        }

        try {
            Long agendamentoId = Long.valueOf(id.substring(prefixo.length()));
            if (agendamentoId <= 0) {
                throw new NumberFormatException();
            }
            if ("CONFIRMAR_CANCELAMENTO_".equals(prefixo)) {
                boolean canceladoAgora = agendamentoService.cancelar(agendamentoId, cliente, barbeiro);
                whatsapp.enviarTextoAposCommit(phoneNumberId, numero, canceladoAgora
                        ? "✅ Agendamento cancelado."
                        : "Esse agendamento já foi cancelado.");
            } else {
                Agendamento agendamento = agendamentoService.buscarParaCancelar(agendamentoId, cliente, barbeiro);
                if ("ESCOLHER_CANCELAMENTO_".equals(prefixo)) {
                    whatsapp.enviarConfirmacaoCancelamento(phoneNumberId, numero, agendamento);
                } else {
                    whatsapp.enviarTextoAposCommit(phoneNumberId, numero, "Seu agendamento foi mantido.");
                }
            }
        } catch (NumberFormatException e) {
            whatsapp.enviarTextoAposCommit(phoneNumberId, numero,
                    "Essa opção é inválida. Toque em Cancelar no menu para escolher novamente.");
        } catch (CancelamentoNaoPermitidoException e) {
            whatsapp.enviarTextoAposCommit(phoneNumberId, numero, e.getMessage());
        }
        return true;
    }

    private void processarOpcao(String id, SessaoConversa sessao, Cliente cliente, Barbeiro barbeiro) {

        if ("CONFIRMAR".equals(id) || "VOLTAR".equals(id)) {
            whatsapp.enviarTextoAposCommit(
                    barbeiro.getWhatsappPhoneNumberId(),
                    cliente.getNumeroTelefone(),
                    "Essa opção foi substituída. Envie oi para começar novamente."
            );
            return;
        }

        if ("AGENDAR".equals(id)) {
            List<Servico> servicos =
                    servicoRepository.findByBarbeiroIdAndAtivoTrue(
                            barbeiro.getId()
                    );
            sessao.limpar();
            if (servicos.isEmpty()) {
                salvarSessao(sessao);
                whatsapp.enviarTextoAposCommit(barbeiro.getWhatsappPhoneNumberId(), cliente.getNumeroTelefone(),
                        "Ainda não há serviços disponíveis para agendamento.");
                return;
            }
            sessao.setEtapa(EtapaConversaEnum.ESCOLHENDO_SERVICO);
            salvarSessao(sessao);
            whatsapp.enviarServicos(barbeiro.getWhatsappPhoneNumberId(),
                    cliente.getNumeroTelefone(), servicos);
            return;
        }

        if (id.startsWith("SERVICO_")) {
            if (!validarEtapa(
                    sessao,
                    EtapaConversaEnum.ESCOLHENDO_SERVICO,
                    barbeiro,
                    cliente
            )) {
                return;
            }

            Long servicoId = Long.valueOf(
                    id.substring("SERVICO_".length())
            );

            Servico servico = servicoRepository
                    .findByIdAndBarbeiroIdAndAtivoTrue(
                            servicoId,
                            barbeiro.getId()
                    )
                    .orElse(null);

            if (servico == null) {
                sessao.limpar();
                salvarSessao(sessao);

                whatsapp.enviarTextoAposCommit(
                        barbeiro.getWhatsappPhoneNumberId(),
                        cliente.getNumeroTelefone(),
                        "Esse serviço não está mais disponível. Inicie um novo agendamento para consultar os serviços disponíveis."
                );

                whatsapp.enviarMenuPrincipal(
                        barbeiro.getWhatsappPhoneNumberId(),
                        cliente.getNumeroTelefone()
                );

                return;
            }

            sessao.setServicoSelecionado(servico);
            sessao.setDataSelecionada(null);
            sessao.setHorarioSelecionado(null);
            sessao.setConfirmacaoId(null);
            sessao.limparValoresConfirmacao();

            sessao.setEtapa(EtapaConversaEnum.ESCOLHENDO_DATA);
            salvarSessao(sessao);

            whatsapp.enviarDatas(
                    barbeiro.getWhatsappPhoneNumberId(),
                    cliente.getNumeroTelefone()
            );

            return;
        }

        if (id.startsWith("DATA_")) {
            if (!validarEtapa(sessao, EtapaConversaEnum.ESCOLHENDO_DATA, barbeiro, cliente)) {
                return;
            }
            sessao.setDataSelecionada(LocalDate.parse(id.substring("DATA_".length())));
            mostrarHorarios(sessao, cliente, barbeiro);
            return;
        }

        if (id.startsWith("PAGINA_HORARIOS_")) {
            if (!validarEtapa(
                    sessao,
                    EtapaConversaEnum.ESCOLHENDO_HORARIO,
                    barbeiro,
                    cliente
            )) {
                return;
            }

            if (sessao.getServicoSelecionado() == null
                    || sessao.getDataSelecionada() == null) {
                whatsapp.enviarTextoAposCommit(
                        barbeiro.getWhatsappPhoneNumberId(),
                        cliente.getNumeroTelefone(),
                        "Envie oi para escolher novamente o serviço e a data."
                );
                return;
            }

            int pagina;

            try {
                pagina = Integer.parseInt(
                        id.substring("PAGINA_HORARIOS_".length())
                );

                if (pagina < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                whatsapp.enviarTextoAposCommit(
                        barbeiro.getWhatsappPhoneNumberId(),
                        cliente.getNumeroTelefone(),
                        "Essa página é inválida. Use a lista mais recente."
                );
                return;
            }

            mostrarHorarios(sessao, cliente, barbeiro, pagina);
            return;
        }

        if (id.startsWith("HORA_")) {
            if (!validarEtapa(
                    sessao,
                    EtapaConversaEnum.ESCOLHENDO_HORARIO,
                    barbeiro,
                    cliente
            )) {
                return;
            }

            LocalTime horario = LocalTime.parse(
                    id.substring("HORA_".length())
            );

            List<LocalTime> disponiveis =
                    disponibilidadeService.buscarHorarios(
                            barbeiro,
                            sessao.getServicoSelecionado(),
                            sessao.getDataSelecionada()
                    );

            if (!disponiveis.contains(horario)) {
                whatsapp.enviarTextoAposCommit(
                        barbeiro.getWhatsappPhoneNumberId(),
                        cliente.getNumeroTelefone(),
                        "Esse horário não está mais disponível. Escolha outro horário."
                );

                mostrarHorarios(sessao, cliente, barbeiro);
                return;
            }

            sessao.setHorarioSelecionado(horario);

            sessao.setPrecoServicoNaConfirmacao(
                    sessao.getServicoSelecionado().getPreco()
            );

            sessao.setDuracaoServicoNaConfirmacao(
                    sessao.getServicoSelecionado().getDuracaoMinutos()
            );

            sessao.setConfirmacaoId(UUID.randomUUID());
            sessao.setEtapa(EtapaConversaEnum.CONFIRMANDO);

            salvarSessao(sessao);

            whatsapp.enviarConfirmacao(
                    barbeiro.getWhatsappPhoneNumberId(),
                    cliente.getNumeroTelefone(),
                    sessao
            );

            return;
        }

        if (id.startsWith("VOLTAR_")) {
            if (validarConfirmacao(
                    id, "VOLTAR_", sessao, barbeiro, cliente
            )) {
                mostrarHorarios(sessao, cliente, barbeiro);
            }
            return;
        }

        if (id.startsWith("CONFIRMAR_")) {
            if (!validarConfirmacao(
                    id, "CONFIRMAR_", sessao, barbeiro, cliente
            )) {
                return;
            }
            if (sessao.getServicoSelecionado() == null || sessao.getDataSelecionada() == null
                    || sessao.getHorarioSelecionado() == null) {
                whatsapp.enviarTextoAposCommit(barbeiro.getWhatsappPhoneNumberId(), cliente.getNumeroTelefone(),
                        "Precisamos refazer sua seleção. Envie oi para começar novamente.");
                return;
            }

            Agendamento agendamento;

            try {
                agendamento = agendamentoService.agendar(
                        cliente,
                        barbeiro,
                        sessao.getServicoSelecionado(),
                        LocalDateTime.of(
                                sessao.getDataSelecionada(),
                                sessao.getHorarioSelecionado()
                        ),
                        sessao.getPrecoServicoNaConfirmacao(),
                        sessao.getDuracaoServicoNaConfirmacao()
                );
            }
            catch (ServicoAlteradoException e) {
                sessao.limpar();
                salvarSessao(sessao);

                whatsapp.enviarTextoAposCommit(
                        barbeiro.getWhatsappPhoneNumberId(),
                        cliente.getNumeroTelefone(),
                        e.getMessage()
                );

                whatsapp.enviarMenuPrincipal(
                        barbeiro.getWhatsappPhoneNumberId(),
                        cliente.getNumeroTelefone()
                );

                return;
            }
            catch (ServicoIndisponivelException e) {
                sessao.limpar();
                salvarSessao(sessao);

                whatsapp.enviarTextoAposCommit(
                        barbeiro.getWhatsappPhoneNumberId(),
                        cliente.getNumeroTelefone(),
                        e.getMessage()
                );

                whatsapp.enviarMenuPrincipal(
                        barbeiro.getWhatsappPhoneNumberId(),
                        cliente.getNumeroTelefone()
                );

                return;
            } catch (HorarioIndisponivelException e) {
                whatsapp.enviarTextoAposCommit(
                        barbeiro.getWhatsappPhoneNumberId(),
                        cliente.getNumeroTelefone(),
                        e.getMessage()
                );

                mostrarHorarios(sessao, cliente, barbeiro);
                return;
            }

            // A transação de agendar já terminou. Não deixa uma confirmação antiga ativa.
            sessao.limpar();
            salvarSessao(sessao);
            whatsapp.enviarTextoAposCommit(barbeiro.getWhatsappPhoneNumberId(), cliente.getNumeroTelefone(),
                    """
                    ✅ Agendamento confirmado!

                    Serviço: %s
                    Data: %s
                    Horário: %s
                    """.formatted(agendamento.getServico().getNome(),
                            agendamento.getInicio().toLocalDate(),
                            agendamento.getInicio().toLocalTime()));
        }
    }

    private void mostrarHorarios(
            SessaoConversa sessao,
            Cliente cliente,
            Barbeiro barbeiro
    ) {
        mostrarHorarios(sessao, cliente, barbeiro, 0);
    }

    private void mostrarHorarios(
            SessaoConversa sessao,
            Cliente cliente,
            Barbeiro barbeiro,
            int pagina
    ) {
        List<LocalTime> horarios = disponibilidadeService.buscarHorarios(
                barbeiro,
                sessao.getServicoSelecionado(),
                sessao.getDataSelecionada()
        );

        sessao.setHorarioSelecionado(null);
        sessao.setConfirmacaoId(null);

        if (horarios.isEmpty()) {
            sessao.setDataSelecionada(null);
            sessao.setEtapa(EtapaConversaEnum.ESCOLHENDO_DATA);
            salvarSessao(sessao);

            whatsapp.enviarTextoAposCommit(
                    barbeiro.getWhatsappPhoneNumberId(),
                    cliente.getNumeroTelefone(),
                    "Não há horários disponíveis nesse dia. Escolha outra data."
            );

            whatsapp.enviarDatas(
                    barbeiro.getWhatsappPhoneNumberId(),
                    cliente.getNumeroTelefone()
            );
            return;
        }

        sessao.setEtapa(EtapaConversaEnum.ESCOLHENDO_HORARIO);
        salvarSessao(sessao);

        whatsapp.enviarHorarios(
                barbeiro.getWhatsappPhoneNumberId(),
                cliente.getNumeroTelefone(),
                horarios,
                pagina
        );
    }

    private boolean validarEtapa(SessaoConversa sessao, EtapaConversaEnum esperada,
                                 Barbeiro barbeiro, Cliente cliente) {
        if (sessao.getEtapa() == esperada) {
            return true;
        }
        whatsapp.enviarTextoAposCommit(barbeiro.getWhatsappPhoneNumberId(), cliente.getNumeroTelefone(),
                "Essa opção é de uma etapa anterior. Use a mensagem mais recente ou envie oi para recomeçar.");
        return false;
    }

    private void salvarSessao(SessaoConversa sessao) {
        sessao.atualizarInteracao();
        sessaoRepository.save(sessao);
    }


    private boolean validarConfirmacao(
            String id,
            String prefixo,
            SessaoConversa sessao,
            Barbeiro barbeiro,
            Cliente cliente
    ) {
        if (!validarEtapa(
                sessao,
                EtapaConversaEnum.CONFIRMANDO,
                barbeiro,
                cliente
        )) {
            return false;
        }

        UUID confirmacaoRecebida;

        try {
            confirmacaoRecebida = UUID.fromString(
                    id.substring(prefixo.length())
            );
        } catch (IllegalArgumentException e) {
            whatsapp.enviarTextoAposCommit(
                    barbeiro.getWhatsappPhoneNumberId(),
                    cliente.getNumeroTelefone(),
                    "Confirmação inválida. Use a mensagem mais recente."
            );
            return false;
        }

        if (!confirmacaoRecebida.equals(sessao.getConfirmacaoId())) {
            whatsapp.enviarTextoAposCommit(
                    barbeiro.getWhatsappPhoneNumberId(),
                    cliente.getNumeroTelefone(),
                    "Essa confirmação pertence a outra seleção. "
                            + "Use a mensagem mais recente."
            );
            return false;
        }

        return true;
    }

}

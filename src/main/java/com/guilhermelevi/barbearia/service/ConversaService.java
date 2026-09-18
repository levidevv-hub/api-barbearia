package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.domain.enums.EtapaConversaEnum;
import com.guilhermelevi.barbearia.domain.exception.HorarioIndisponivelException;
import com.guilhermelevi.barbearia.domain.exception.CancelamentoNaoPermitidoException;
import com.guilhermelevi.barbearia.domain.exception.OperacaoAdministrativaException;
import com.guilhermelevi.barbearia.domain.exception.ServicoIndisponivelException;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.WhatsAppClient;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import com.guilhermelevi.barbearia.repositories.IServicoRepository;
import com.guilhermelevi.barbearia.repositories.ISessaoConversaRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import com.guilhermelevi.barbearia.domain.exception.ServicoAlteradoException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

@Service
public class ConversaService {

    private final IBarbeiroRepository barbeiroRepository;
    private final ClienteService clienteService;
    private final IServicoRepository servicoRepository;
    private final ISessaoConversaRepository sessaoRepository;
    private final DisponibilidadeService disponibilidadeService;
    private final AgendamentoService agendamentoService;
    private final WhatsAppClient whatsapp;
    private final AutorizacaoBarbeiroService autorizacaoBarbeiroService;
    private final BloqueioDataService bloqueioDataService;
    private final PreviaBloqueioService previaBloqueioService;
    private final ServicoService servicoService;

    public ConversaService(
            IBarbeiroRepository barbeiroRepository,
            ClienteService clienteService,
            IServicoRepository servicoRepository,
            ISessaoConversaRepository sessaoRepository,
            DisponibilidadeService disponibilidadeService,
            AgendamentoService agendamentoService,
            WhatsAppClient whatsapp, AutorizacaoBarbeiroService autorizacaoBarbeiroService, BloqueioDataService bloqueioDataService, PreviaBloqueioService previaBloqueioService, ServicoService servicoService
    ) {
        this.barbeiroRepository = barbeiroRepository;
        this.clienteService = clienteService;
        this.servicoRepository = servicoRepository;
        this.sessaoRepository = sessaoRepository;
        this.disponibilidadeService = disponibilidadeService;
        this.agendamentoService = agendamentoService;
        this.whatsapp = whatsapp;
        this.autorizacaoBarbeiroService = autorizacaoBarbeiroService;
        this.bloqueioDataService = bloqueioDataService;
        this.previaBloqueioService = previaBloqueioService;
        this.servicoService = servicoService;
    }

    public void processar(JsonNode payload) {

        JsonNode value = payload
                .path("entry")
                .path(0)
                .path("changes")
                .path(0)
                .path("value");

        JsonNode messages =
                value.path("messages");

        if (!messages.isArray()
                || messages.isEmpty()) {
            return;
        }

        JsonNode mensagem =
                messages.get(0);

        String telefoneCliente =
                mensagem.path("from").asText();

        String phoneNumberId =
                value.path("metadata")
                        .path("phone_number_id")
                        .asText();

        String nomeCliente =
                value.path("contacts")
                        .path(0)
                        .path("profile")
                        .path("name")
                        .asText("Cliente");

        Barbeiro barbeiro =
                barbeiroRepository
                        .findByWhatsappPhoneNumberId(
                                phoneNumberId
                        )
                        .orElseThrow();

        Cliente cliente =
                clienteService.buscarOuCriar(
                        nomeCliente,
                        telefoneCliente
                );

        String tipo =
                mensagem.path("type").asText();

        if ("text".equals(tipo)) {
            String texto = mensagem.path("text")
                    .path("body")
                    .asText("")
                    .strip();
            if (processarRespostaCadastroServico(
                    texto,
                    barbeiro,
                    telefoneCliente
            )) {
                return;
            }

            if (processarRespostaEdicaoServico(
                    texto,
                    barbeiro,
                    telefoneCliente
            )) {
                return;
            }

            if (processarPreviaBloqueio(texto, barbeiro, telefoneCliente)) {
                return;
            }

            if (processarLiberacaoDia(texto, barbeiro, telefoneCliente)) {
                return;
            }

            if (processarConsultaAgenda(texto, barbeiro, telefoneCliente)) {
                return;
            }

            if ("minha agenda".equalsIgnoreCase(texto)) {
                boolean autorizado =
                        autorizacaoBarbeiroService.podeAdministrar(
                                barbeiro.getId(),
                                telefoneCliente
                        );

                if (!autorizado) {
                    whatsapp.enviarTextoAposCommit(
                            phoneNumberId,
                            telefoneCliente,
                            "Esse número não tem permissão para administrar esta agenda."
                    );
                    return;
                }

                whatsapp.enviarMenuAdministrador(
                        phoneNumberId,
                        telefoneCliente
                );
                return;
            }

            iniciar(
                    barbeiro,
                    cliente,
                    telefoneCliente
            );
            return;
        }

        if ("interactive".equals(tipo)) {

            processarInteracao(
                    barbeiro,
                    cliente,
                    telefoneCliente,
                    mensagem
            );
        }
    }

    private void iniciar(Barbeiro barbeiro, Cliente cliente, String telefone) {

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

    private void processarInteracao(Barbeiro barbeiro, Cliente cliente, String telefone, JsonNode mensagem) {

        JsonNode interactive =
                mensagem.path("interactive");

        String id;

        if (interactive.has("button_reply")) {

            id = interactive
                    .path("button_reply")
                    .path("id")
                    .asText();

        } else {

            id = interactive
                    .path("list_reply")
                    .path("id")
                    .asText();
        }

        if (id.startsWith("ADMIN_")) {
            boolean autorizado =
                    autorizacaoBarbeiroService.podeAdministrar(
                            barbeiro.getId(),
                            telefone
                    );

            if (!autorizado) {
                whatsapp.enviarTextoAposCommit(
                        barbeiro.getWhatsappPhoneNumberId(),
                        telefone,
                        "Esse número não tem permissão para administrar esta agenda."
                );
                return;
            }

            if (id.startsWith("ADMIN_CONFIRMAR_BLOQUEIO_")) {
                UUID previaId;

                try {
                    previaId = UUID.fromString(
                            id.substring("ADMIN_CONFIRMAR_BLOQUEIO_".length())
                    );
                } catch (IllegalArgumentException e) {
                    whatsapp.enviarTextoAposCommit(
                            barbeiro.getWhatsappPhoneNumberId(),
                            telefone,
                            "Confirmação inválida. Solicite uma nova prévia."
                    );
                    return;
                }

                if (id.startsWith("ADMIN_EDITAR_SERVICO_")) {
                    selecionarServicoParaEdicao(id, barbeiro, telefone);
                    return;
                }

                int cancelados;

                try {
                    cancelados = previaBloqueioService.confirmar(
                            previaId,
                            barbeiro.getId(),
                            telefone
                    );
                } catch (OperacaoAdministrativaException e) {
                    whatsapp.enviarTextoAposCommit(
                            barbeiro.getWhatsappPhoneNumberId(),
                            telefone,
                            e.getMessage()
                    );
                    return;
                }

                whatsapp.enviarTextoAposCommit(
                        barbeiro.getWhatsappPhoneNumberId(),
                        telefone,
                        """
                        Dia bloqueado com sucesso.
            
                        Agendamentos cancelados: %d.
                        Avisos aos clientes registrados para envio.
                        """.formatted(cancelados)
                );
                return;
            }

            if (id.startsWith("ADMIN_EDITAR_SERVICO_")) {
                selecionarServicoParaEdicao(id, barbeiro, telefone);
                return;
            }

            if (id.startsWith("ADMIN_ATIVAR_SERVICO_")) {
                processarAlteracaoStatusServico(
                        id,
                        barbeiro,
                        telefone,
                        true
                );
                return;
            }

            if (id.startsWith("ADMIN_DESATIVAR_SERVICO_")) {
                processarAlteracaoStatusServico(
                        id,
                        barbeiro,
                        telefone,
                        false
                );
                return;
            }

            switch (id) {
                case "ADMIN_BLOQUEAR_DIA" ->
                        whatsapp.enviarTextoAposCommit(
                                barbeiro.getWhatsappPhoneNumberId(),
                                telefone,
                                "Para consultar o bloqueio de uma data, envie:\n\n"
                                        + "Bloquear 10/09/2026\n\n"
                                        + "Use a data desejada no formato dia/mês/ano. "
                                        + "Você verá os agendamentos afetados "
                                        + "antes de confirmar."
                        );

                case "ADMIN_LIBERAR_DIA" ->
                        whatsapp.enviarTextoAposCommit(
                                barbeiro.getWhatsappPhoneNumberId(),
                                telefone,
                                """
                                Para remover o bloqueio de uma data, envie:
                
                                Liberar 10/09/2026
                
                                Use a data desejada. O comando remove o bloqueio
                                imediatamente e mantém o expediente e as pausas.
                                Reservas canceladas não serão restauradas.
                                """
                        );

                case "ADMIN_VER_AGENDAMENTOS" ->
                        whatsapp.enviarTextoAposCommit(
                                barbeiro.getWhatsappPhoneNumberId(),
                                telefone,
                                """
                                Para consultar os agendamentos de uma data, envie:
                
                                Agenda 10/09/2026
                
                                Use a data desejada no formato dia/mês/ano.
                                """
                        );
                case "ADMIN_CADASTRAR_SERVICO" ->
                        iniciarCadastroServico(barbeiro, telefone);
                case "ADMIN_EDITAR_SERVICO" ->
                        iniciarEdicaoServico(barbeiro, telefone);
                case "ADMIN_ATIVAR_SERVICO" ->
                        mostrarServicosParaAlterarStatus(
                                barbeiro,
                                telefone,
                                true
                        );

                case "ADMIN_DESATIVAR_SERVICO" ->
                        mostrarServicosParaAlterarStatus(
                                barbeiro,
                                telefone,
                                false
                        );
                default ->
                        whatsapp.enviarTextoAposCommit(
                                barbeiro.getWhatsappPhoneNumberId(),
                                telefone,
                                "Opção administrativa inválida. Envie Minha agenda."
                        );
            }

            return;
        }

        // A consulta não depende da etapa da conversa nem altera uma seleção em andamento.
        if ("CONSULTAR".equals(id)) {
            List<Agendamento> agendamentos = agendamentoService.listarProximos(cliente, barbeiro);
            whatsapp.enviarMeusHorarios(
                    barbeiro.getWhatsappPhoneNumberId(), cliente.getNumeroTelefone(), agendamentos);
            return;
        }

        if (processarCancelamento(id, cliente, barbeiro)) {
            return;
        }

        SessaoConversa sessao =
                sessaoRepository
                        .findByNumeroClienteAndBarbeiroId(
                                telefone,
                                barbeiro.getId()
                        )
                        .orElseThrow();

        processarOpcao(
                id,
                sessao,
                cliente,
                barbeiro
        );
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

    private boolean processarPreviaBloqueio(
            String texto,
            Barbeiro barbeiro,
            String telefone
    ) {
        String[] partes = texto.strip().split("\\s+", 2);

        if (!"bloquear".equalsIgnoreCase(partes[0])) {
            return false;
        }

        String phoneNumberId = barbeiro.getWhatsappPhoneNumberId();

        if (!autorizacaoBarbeiroService.podeAdministrar(
                barbeiro.getId(), telefone
        )) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Esse número não tem permissão para administrar esta agenda."
            );
            return true;
        }

        if (partes.length != 2) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Informe a data. Exemplo: Bloquear 10/09/2026"
            );
            return true;
        }

        DateTimeFormatter formatoData = DateTimeFormatter
                .ofPattern("dd/MM/uuuu")
                .withResolverStyle(ResolverStyle.STRICT);

        LocalDate data;

        try {
            data = LocalDate.parse(partes[1].strip(), formatoData);
        } catch (DateTimeParseException e) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Data inválida. Use dia/mês/ano, como 10/09/2026."
            );
            return true;
        }

        PreviaBloqueioService.ResultadoPrevia previa;

        try {
            previa = previaBloqueioService.criar(
                    barbeiro.getId(),
                    telefone,
                    data
            );
        } catch (OperacaoAdministrativaException e) {
            whatsapp.enviarTextoAposCommit(phoneNumberId, telefone, e.getMessage());
            return true;
        }

        List<BloqueioDataService.ReservaAfetada> afetados = previa.afetados();

        StringBuilder mensagem = new StringBuilder(
                "Prévia do bloqueio de " + data.format(formatoData)
                        + "\nAgendamentos futuros afetados: "
                        + afetados.size() + "\n\n"
        );

        DateTimeFormatter formatoHora =
                DateTimeFormatter.ofPattern("HH:mm");

        for (BloqueioDataService.ReservaAfetada reserva : afetados) {
            String item = """
                Reserva #%d
                Cliente: %.100s
                Serviço: %.100s
                Horário: %s

                """.formatted(
                    reserva.agendamentoId(),
                    reserva.cliente(),
                    reserva.servico(),
                    reserva.inicio().format(formatoHora)
            );

            // Divide a prévia em mensagens quando houver muitas reservas.
            if (mensagem.length() + item.length() > 3500) {
                whatsapp.enviarTextoAposCommit(
                        phoneNumberId, telefone, mensagem.toString()
                );
                mensagem = new StringBuilder("Continuação da prévia:\n\n");
            }

            mensagem.append(item);
        }

        if (afetados.isEmpty()) {
            mensagem.append(
                    "Não há agendamentos futuros afetados nessa data.\n\n"
            );
        }

        mensagem.append(
                "Esta é apenas uma consulta. "
                        + "Nenhum bloqueio ou cancelamento foi realizado."
        );

        whatsapp.enviarTextoAposCommit(phoneNumberId, telefone, mensagem.toString());

        whatsapp.enviarConfirmacaoBloqueio(
                phoneNumberId,
                telefone,
                previa.id(),
                previa.data(),
                afetados.size()
        );

        return true;
    }

    private boolean processarLiberacaoDia(
            String texto,
            Barbeiro barbeiro,
            String telefone
    ) {
        String[] partes = texto.strip().split("\\s+", 2);

        if (!"liberar".equalsIgnoreCase(partes[0])) {
            return false;
        }

        String phoneNumberId = barbeiro.getWhatsappPhoneNumberId();

        if (!autorizacaoBarbeiroService.podeAdministrar(
                barbeiro.getId(), telefone
        )) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Esse número não tem permissão para administrar esta agenda."
            );
            return true;
        }

        if (partes.length != 2) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Informe a data. Exemplo: Liberar 10/09/2026"
            );
            return true;
        }

        DateTimeFormatter formatoData = DateTimeFormatter
                .ofPattern("dd/MM/uuuu")
                .withResolverStyle(ResolverStyle.STRICT);

        LocalDate data;

        try {
            data = LocalDate.parse(partes[1].strip(), formatoData);
        } catch (DateTimeParseException e) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Data inválida. Use dia/mês/ano, como 10/09/2026."
            );
            return true;
        }

        boolean removido;

        try {
            removido = bloqueioDataService.liberarDia(
                    barbeiro.getId(),
                    telefone,
                    data
            );
        } catch (OperacaoAdministrativaException e) {
            whatsapp.enviarTextoAposCommit(phoneNumberId, telefone, e.getMessage());
            return true;
        }

        String resposta = removido
                ? """
              Bloqueio de %s removido.

              A disponibilidade volta a seguir o expediente semanal
              e as pausas. Reservas canceladas não foram restauradas.
              """.formatted(data.format(formatoData))
                : "Não existe bloqueio excepcional para "
                + data.format(formatoData) + ".";

        whatsapp.enviarTextoAposCommit(phoneNumberId, telefone, resposta);
        return true;
    }

    private boolean processarConsultaAgenda(
            String texto,
            Barbeiro barbeiro,
            String telefone
    ) {
        String[] partes = texto.strip().split("\\s+", 2);

        if (!"agenda".equalsIgnoreCase(partes[0])) {
            return false;
        }

        String phoneNumberId = barbeiro.getWhatsappPhoneNumberId();

        if (!autorizacaoBarbeiroService.podeAdministrar(
                barbeiro.getId(), telefone
        )) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Esse número não tem permissão para consultar esta agenda."
            );
            return true;
        }

        if (partes.length != 2) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Informe a data. Exemplo: Agenda 10/09/2026"
            );
            return true;
        }

        DateTimeFormatter formatoData = DateTimeFormatter
                .ofPattern("dd/MM/uuuu")
                .withResolverStyle(ResolverStyle.STRICT);

        LocalDate data;

        try {
            data = LocalDate.parse(partes[1].strip(), formatoData);
        } catch (DateTimeParseException e) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Data inválida. Use dia/mês/ano, como 10/09/2026."
            );
            return true;
        }

        List<Agendamento> agendamentos;

        try {
            agendamentos = agendamentoService.listarAgendaDoDia(
                    barbeiro.getId(),
                    telefone,
                    data
            );
        } catch (OperacaoAdministrativaException e) {
            whatsapp.enviarTextoAposCommit(phoneNumberId, telefone, e.getMessage());
            return true;
        }

        whatsapp.enviarAgendaDoDia(
                phoneNumberId,
                telefone,
                data,
                agendamentos
        );

        return true;
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

    private void iniciarCadastroServico(
            Barbeiro barbeiro,
            String telefone
    ) {
        SessaoConversa sessao = sessaoRepository
                .findByNumeroClienteAndBarbeiroId(
                        telefone,
                        barbeiro.getId()
                )
                .orElseGet(SessaoConversa::new);

        sessao.setNumeroCliente(telefone);
        sessao.setBarbeiro(barbeiro);

        sessao.limpar();

        sessao.setEtapa(
                EtapaConversaEnum.CADASTRANDO_SERVICO_NOME
        );

        salvarSessao(sessao);

        whatsapp.enviarTextoAposCommit(
                barbeiro.getWhatsappPhoneNumberId(),
                telefone,
                """
                Vamos cadastrar um novo serviço.
    
                Qual é o nome do serviço?
                Exemplo: Corte degradê
                """
        );
    }

    private boolean processarRespostaCadastroServico(
            String texto,
            Barbeiro barbeiro,
            String telefone
    ) {
        SessaoConversa sessao = sessaoRepository
                .findByNumeroClienteAndBarbeiroId(
                        telefone,
                        barbeiro.getId()
                )
                .orElse(null);

        if (sessao == null || sessao.getEtapa() == null) {
            return false;
        }

        boolean cadastrando = switch (sessao.getEtapa()) {
            case CADASTRANDO_SERVICO_NOME,
                 CADASTRANDO_SERVICO_PRECO,
                 CADASTRANDO_SERVICO_DURACAO,
                 CONFIRMANDO_CADASTRO_SERVICO -> true;

            default -> false;
        };

        if (!cadastrando) {
            return false;
        }

        String phoneNumberId = barbeiro.getWhatsappPhoneNumberId();

        if (!autorizacaoBarbeiroService.podeAdministrar(
                barbeiro.getId(),
                telefone
        )) {
            sessao.limpar();
            salvarSessao(sessao);

            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Esse número não tem permissão para cadastrar serviços."
            );

            return true;
        }

        if ("cancelar".equalsIgnoreCase(texto)
                || "minha agenda".equalsIgnoreCase(texto)) {

            sessao.limpar();
            salvarSessao(sessao);

            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Cadastro de serviço cancelado."
            );

            whatsapp.enviarMenuAdministrador(
                    phoneNumberId,
                    telefone
            );

            return true;
        }

        try {
            switch (sessao.getEtapa()) {
                case CADASTRANDO_SERVICO_NOME -> {
                    if (texto.isBlank() || texto.length() > 100) {
                        throw new OperacaoAdministrativaException(
                                "Envie um nome com 1 a 100 caracteres."
                        );
                    }

                    sessao.setNomeServicoEmCadastro(texto.strip());
                    sessao.setEtapa(
                            EtapaConversaEnum.CADASTRANDO_SERVICO_PRECO
                    );

                    salvarSessao(sessao);

                    whatsapp.enviarTextoAposCommit(
                            phoneNumberId,
                            telefone,
                            """
                            Qual é o preço do serviço?
    
                            Exemplo: 35 ou 35,50
                            Envie sem separador de milhar.
    
                            Para desistir, digite cancelar.
                            """
                    );
                }

                case CADASTRANDO_SERVICO_PRECO -> {
                    BigDecimal preco = interpretarPrecoServico(texto);

                    sessao.setPrecoServicoEmCadastro(preco);
                    sessao.setEtapa(
                            EtapaConversaEnum.CADASTRANDO_SERVICO_DURACAO
                    );

                    salvarSessao(sessao);

                    whatsapp.enviarTextoAposCommit(
                            phoneNumberId,
                            telefone,
                            """
                            Quanto tempo dura o serviço, em minutos?
    
                            Exemplo: 30
                            Envie somente um número inteiro maior que zero.
    
                            Para desistir, digite cancelar.
                            """
                    );
                }

                case CADASTRANDO_SERVICO_DURACAO -> {
                    int duracao = interpretarDuracaoServico(texto);

                    sessao.setDuracaoServicoEmCadastro(duracao);
                    sessao.setEtapa(
                            EtapaConversaEnum.CONFIRMANDO_CADASTRO_SERVICO
                    );

                    salvarSessao(sessao);

                    NumberFormat moeda = NumberFormat.getCurrencyInstance(
                            Locale.forLanguageTag("pt-BR")
                    );

                    whatsapp.enviarTextoAposCommit(
                            phoneNumberId,
                            telefone,
                            """
                            Confira o novo serviço:
    
                            Nome: %s
                            Preço: %s
                            Duração: %d minutos
    
                            Digite confirmar para cadastrar.
                            Ou cancelar para descartar.
                            """.formatted(
                                    sessao.getNomeServicoEmCadastro(),
                                    moeda.format(
                                            sessao.getPrecoServicoEmCadastro()
                                    ),
                                    sessao.getDuracaoServicoEmCadastro()
                            )
                    );
                }

                case CONFIRMANDO_CADASTRO_SERVICO -> {
                    if (!"confirmar".equalsIgnoreCase(texto)) {
                        whatsapp.enviarTextoAposCommit(
                                phoneNumberId,
                                telefone,
                                "Digite confirmar para cadastrar ou cancelar para descartar."
                        );

                        return true;
                    }

                    Servico servico = servicoService.cadastrar(
                            barbeiro.getId(),
                            telefone,
                            sessao.getNomeServicoEmCadastro(),
                            sessao.getPrecoServicoEmCadastro(),
                            sessao.getDuracaoServicoEmCadastro()
                    );

                    sessao.limpar();
                    salvarSessao(sessao);

                    whatsapp.enviarTextoAposCommit(
                            phoneNumberId,
                            telefone,
                            "Serviço \"%s\" cadastrado com sucesso!"
                                    .formatted(servico.getNome())
                    );

                    whatsapp.enviarMenuAdministrador(
                            phoneNumberId,
                            telefone
                    );
                }

                default -> {
                    return false;
                }
            }
        } catch (OperacaoAdministrativaException exception) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    exception.getMessage()
                            + "\n\nPara desistir, digite cancelar."
            );
        }

        return true;
    }

    private BigDecimal interpretarPrecoServico(String texto) {
        String valor = texto
                .replaceFirst("(?i)^R\\$\\s*", "")
                .strip();

        if (!valor.matches("[0-9]+([.,][0-9]{1,2})?")) {
            throw new OperacaoAdministrativaException(
                    "Preço inválido. Envie, por exemplo, 35 ou 35,50, sem separador de milhar."
            );
        }

        return new BigDecimal(valor.replace(',', '.'));
    }

    private int interpretarDuracaoServico(String texto) {
        if (!texto.matches("[0-9]+")) {
            throw new OperacaoAdministrativaException(
                    "Duração inválida. Envie somente os minutos, por exemplo: 30."
            );
        }

        try {
            int minutos = Integer.parseInt(texto);

            if (minutos <= 0) {
                throw new OperacaoAdministrativaException(
                        "A duração deve ser maior que zero."
                );
            }

            return minutos;
        } catch (NumberFormatException exception) {
            throw new OperacaoAdministrativaException(
                    "A duração informada é muito grande. Envie um número menor de minutos."
            );
        }
    }

    private void iniciarEdicaoServico(
            Barbeiro barbeiro,
            String telefone
    ) {
        List<Servico> servicos = servicoRepository
                .findByBarbeiroId(barbeiro.getId());

        SessaoConversa sessao = sessaoRepository
                .findByNumeroClienteAndBarbeiroId(
                        telefone,
                        barbeiro.getId()
                )
                .orElseGet(SessaoConversa::new);

        sessao.setNumeroCliente(telefone);
        sessao.setBarbeiro(barbeiro);
        sessao.limpar();

        if (!servicos.isEmpty()) {
            sessao.setEtapa(
                    EtapaConversaEnum.ESCOLHENDO_SERVICO_EDICAO
            );
        }

        salvarSessao(sessao);

        whatsapp.enviarServicosParaEdicao(
                barbeiro.getWhatsappPhoneNumberId(),
                telefone,
                servicos
        );
    }

    private void selecionarServicoParaEdicao(
            String id,
            Barbeiro barbeiro,
            String telefone
    ) {
        String phoneNumberId = barbeiro.getWhatsappPhoneNumberId();

        SessaoConversa sessao = sessaoRepository
                .findByNumeroClienteAndBarbeiroId(
                        telefone,
                        barbeiro.getId()
                )
                .orElse(null);

        if (sessao == null
                || sessao.getEtapa()
                != EtapaConversaEnum.ESCOLHENDO_SERVICO_EDICAO) {

            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Envie Minha agenda e escolha Editar serviço para iniciar uma edição."
            );
            return;
        }

        Long servicoId;

        try {
            servicoId = Long.valueOf(
                    id.substring("ADMIN_EDITAR_SERVICO_".length())
            );
        } catch (NumberFormatException exception) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Opção inválida. Selecione um serviço na lista."
            );
            return;
        }

        Servico servico = servicoRepository
                .findByIdAndBarbeiroId(
                        servicoId,
                        barbeiro.getId()
                )
                .orElse(null);

        if (servico == null) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Esse serviço não está disponível nesta barbearia. Envie Minha agenda para recomeçar."
            );
            return;
        }

        sessao.limparEdicaoServico();

        sessao.setServicoEmEdicao(servico);
        sessao.setNomeServicoEmEdicao(servico.getNome());
        sessao.setPrecoServicoEmEdicao(servico.getPreco());
        sessao.setDuracaoServicoEmEdicao(
                servico.getDuracaoMinutos()
        );

        sessao.setEtapa(
                EtapaConversaEnum.EDITANDO_SERVICO_NOME
        );

        salvarSessao(sessao);

        whatsapp.enviarTextoAposCommit(
                phoneNumberId,
                telefone,
                """
                Vamos editar este serviço.
    
                Nome atual: %s
    
                Envie o novo nome ou digite manter para continuar com o atual.
    
                Para desistir, digite cancelar.
                """.formatted(servico.getNome())
        );
    }

    private boolean processarRespostaEdicaoServico(
            String texto,
            Barbeiro barbeiro,
            String telefone
    ) {
        SessaoConversa sessao = sessaoRepository
                .findByNumeroClienteAndBarbeiroId(
                        telefone,
                        barbeiro.getId()
                )
                .orElse(null);

        if (sessao == null || sessao.getEtapa() == null) {
            return false;
        }

        boolean editando = switch (sessao.getEtapa()) {
            case ESCOLHENDO_SERVICO_EDICAO,
                 EDITANDO_SERVICO_NOME,
                 EDITANDO_SERVICO_PRECO,
                 EDITANDO_SERVICO_DURACAO,
                 CONFIRMANDO_EDICAO_SERVICO -> true;

            default -> false;
        };

        if (!editando) {
            return false;
        }

        String phoneNumberId = barbeiro.getWhatsappPhoneNumberId();

        if (!autorizacaoBarbeiroService.podeAdministrar(
                barbeiro.getId(),
                telefone
        )) {
            sessao.limpar();
            salvarSessao(sessao);

            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Esse número não tem permissão para editar serviços."
            );
            return true;
        }

        if ("cancelar".equalsIgnoreCase(texto)
                || "minha agenda".equalsIgnoreCase(texto)) {

            sessao.limpar();
            salvarSessao(sessao);

            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Edição cancelada. O serviço não foi alterado."
            );

            whatsapp.enviarMenuAdministrador(phoneNumberId, telefone);
            return true;
        }

        if (sessao.getEtapa()
                == EtapaConversaEnum.ESCOLHENDO_SERVICO_EDICAO) {

            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Selecione um serviço na lista ou digite cancelar."
            );
            return true;
        }

        if (sessao.getServicoEmEdicao() == null) {
            sessao.limpar();
            salvarSessao(sessao);

            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Não encontrei o serviço em edição. Selecione Editar serviço novamente."
            );

            whatsapp.enviarMenuAdministrador(phoneNumberId, telefone);
            return true;
        }

        boolean manter = "manter".equalsIgnoreCase(texto);

        NumberFormat moeda = NumberFormat.getCurrencyInstance(
                Locale.forLanguageTag("pt-BR")
        );

        try {
            switch (sessao.getEtapa()) {
                case EDITANDO_SERVICO_NOME -> {
                    String nome = manter
                            ? sessao.getNomeServicoEmEdicao()
                            : texto.strip();

                    if (nome == null || nome.isBlank()
                            || nome.length() > 100) {
                        throw new OperacaoAdministrativaException(
                                "Envie um nome com 1 a 100 caracteres."
                        );
                    }

                    sessao.setNomeServicoEmEdicao(nome);
                    sessao.setEtapa(
                            EtapaConversaEnum.EDITANDO_SERVICO_PRECO
                    );
                    salvarSessao(sessao);

                    String precoAtual =
                            sessao.getPrecoServicoEmEdicao() == null
                                    ? "Não informado"
                                    : moeda.format(
                                    sessao.getPrecoServicoEmEdicao()
                            );

                    whatsapp.enviarTextoAposCommit(
                            phoneNumberId,
                            telefone,
                            """
                            Preço atual: %s
    
                            Envie o novo preço, por exemplo: 35,50.
                            Ou digite manter para continuar com o atual.
    
                            Para desistir, digite cancelar.
                            """.formatted(precoAtual)
                    );
                }

                case EDITANDO_SERVICO_PRECO -> {
                    BigDecimal preco = manter
                            ? sessao.getPrecoServicoEmEdicao()
                            : interpretarPrecoServico(texto);

                    if (preco == null || preco.signum() < 0) {
                        throw new OperacaoAdministrativaException(
                                "Informe um preço válido, por exemplo: 35,50."
                        );
                    }

                    sessao.setPrecoServicoEmEdicao(preco);
                    sessao.setEtapa(
                            EtapaConversaEnum.EDITANDO_SERVICO_DURACAO
                    );
                    salvarSessao(sessao);

                    String duracaoAtual =
                            sessao.getDuracaoServicoEmEdicao() == null
                                    ? "Não informada"
                                    : sessao.getDuracaoServicoEmEdicao()
                                    + " minutos";

                    whatsapp.enviarTextoAposCommit(
                            phoneNumberId,
                            telefone,
                            """
                            Duração atual: %s
    
                            Envie a nova duração em minutos, por exemplo: 30.
                            Ou digite manter para continuar com a atual.
    
                            Para desistir, digite cancelar.
                            """.formatted(duracaoAtual)
                    );
                }

                case EDITANDO_SERVICO_DURACAO -> {
                    Integer duracao = manter
                            ? sessao.getDuracaoServicoEmEdicao()
                            : interpretarDuracaoServico(texto);

                    if (duracao == null || duracao <= 0) {
                        throw new OperacaoAdministrativaException(
                                "Informe uma duração maior que zero, em minutos."
                        );
                    }

                    sessao.setDuracaoServicoEmEdicao(duracao);
                    sessao.setEtapa(
                            EtapaConversaEnum.CONFIRMANDO_EDICAO_SERVICO
                    );
                    salvarSessao(sessao);

                    whatsapp.enviarTextoAposCommit(
                            phoneNumberId,
                            telefone,
                            """
                            Confira como o serviço ficará:
    
                            Nome: %s
                            Preço: %s
                            Duração: %d minutos
    
                            Digite confirmar para salvar.
                            Ou cancelar para descartar a edição.
                            """.formatted(
                                    sessao.getNomeServicoEmEdicao(),
                                    moeda.format(
                                            sessao.getPrecoServicoEmEdicao()
                                    ),
                                    sessao.getDuracaoServicoEmEdicao()
                            )
                    );
                }

                case CONFIRMANDO_EDICAO_SERVICO -> {
                    if (!"confirmar".equalsIgnoreCase(texto)) {
                        whatsapp.enviarTextoAposCommit(
                                phoneNumberId,
                                telefone,
                                "Digite confirmar para salvar ou cancelar para descartar."
                        );
                        return true;
                    }

                    Servico servico = servicoService.editar(
                            barbeiro.getId(),
                            telefone,
                            sessao.getServicoEmEdicao().getId(),
                            sessao.getNomeServicoEmEdicao(),
                            sessao.getPrecoServicoEmEdicao(),
                            sessao.getDuracaoServicoEmEdicao()
                    );

                    sessao.limpar();
                    salvarSessao(sessao);

                    whatsapp.enviarTextoAposCommit(
                            phoneNumberId,
                            telefone,
                            "Serviço \"%s\" atualizado com sucesso!"
                                    .formatted(servico.getNome())
                    );

                    whatsapp.enviarMenuAdministrador(
                            phoneNumberId,
                            telefone
                    );
                }

                default -> {
                    return false;
                }
            }
        } catch (OperacaoAdministrativaException exception) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    exception.getMessage()
                            + "\n\nPara desistir, digite cancelar."
            );
        }

        return true;
    }

    private void mostrarServicosParaAlterarStatus(
            Barbeiro barbeiro,
            String telefone,
            boolean ativar
    ) {
        SessaoConversa sessao = sessaoRepository
                .findByNumeroClienteAndBarbeiroId(
                        telefone,
                        barbeiro.getId()
                )
                .orElseGet(SessaoConversa::new);

        sessao.setNumeroCliente(telefone);
        sessao.setBarbeiro(barbeiro);
        sessao.limpar();
        salvarSessao(sessao);

        List<Servico> servicos = servicoRepository
                .findByBarbeiroId(barbeiro.getId());

        whatsapp.enviarServicosParaAlterarStatus(
                barbeiro.getWhatsappPhoneNumberId(),
                telefone,
                servicos,
                ativar
        );
    }

    private void processarAlteracaoStatusServico(
            String id,
            Barbeiro barbeiro,
            String telefone,
            boolean ativar
    ) {
        String phoneNumberId = barbeiro.getWhatsappPhoneNumberId();

        String prefixo = ativar
                ? "ADMIN_ATIVAR_SERVICO_"
                : "ADMIN_DESATIVAR_SERVICO_";

        Long servicoId;

        try {
            servicoId = Long.valueOf(
                    id.substring(prefixo.length())
            );
        } catch (NumberFormatException exception) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Opção inválida. Envie Minha agenda e selecione o serviço novamente."
            );
            return;
        }

        try {
            Servico servico = servicoService.alterarStatus(
                    barbeiro.getId(),
                    telefone,
                    servicoId,
                    ativar
            );

            sessaoRepository
                    .findByNumeroClienteAndBarbeiroId(
                            telefone,
                            barbeiro.getId()
                    )
                    .ifPresent(sessao -> {
                        sessao.limpar();
                        salvarSessao(sessao);
                    });

            String mensagem = ativar
                    ? """
                  Serviço "%s" ativado.

                  Ele está disponível para novos agendamentos.
                  """
                    : """
                  Serviço "%s" desativado.

                  Ele não aceita novos agendamentos.
                  As reservas já confirmadas foram mantidas.
                  """;

            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    mensagem.formatted(servico.getNome())
            );

            whatsapp.enviarMenuAdministrador(
                    phoneNumberId,
                    telefone
            );
        } catch (OperacaoAdministrativaException exception) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    exception.getMessage()
            );
        }
    }
}
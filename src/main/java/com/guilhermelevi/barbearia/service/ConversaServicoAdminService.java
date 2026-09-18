package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.domain.Servico;
import com.guilhermelevi.barbearia.domain.SessaoConversa;
import com.guilhermelevi.barbearia.domain.enums.EtapaConversaEnum;
import com.guilhermelevi.barbearia.domain.exception.OperacaoAdministrativaException;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.WhatsAppClient;
import com.guilhermelevi.barbearia.repositories.IServicoRepository;
import com.guilhermelevi.barbearia.repositories.ISessaoConversaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ConversaServicoAdminService {

    private final IServicoRepository servicoRepository;
    private final ISessaoConversaRepository sessaoRepository;
    private final AutorizacaoBarbeiroService autorizacaoBarbeiroService;
    private final ServicoService servicoService;
    private final WhatsAppClient whatsapp;

    public void iniciarCadastroServico(
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

    public boolean processarRespostaCadastroServico(
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

    public void iniciarEdicaoServico(
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

    public void selecionarServicoParaEdicao(
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

    public boolean processarRespostaEdicaoServico(
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

    public void mostrarServicosParaAlterarStatus(
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

    public void processarAlteracaoStatusServico(
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

    private void salvarSessao(SessaoConversa sessao) {
        sessao.atualizarInteracao();
        sessaoRepository.save(sessao);
    }
}

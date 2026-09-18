package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import com.guilhermelevi.barbearia.domain.Agendamento;
import com.guilhermelevi.barbearia.domain.Servico;
import com.guilhermelevi.barbearia.domain.SessaoConversa;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.text.NumberFormat;
import java.util.Locale;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WhatsAppClient {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final int LIMITE_TEXTO = 4096;
    private static final int LIMITE_OPCOES_LISTA = 10;

    private final RestClient restClient;

    @Value("${whatsapp.token}")
    private String token;

    @Value("${whatsapp.api-version}")
    private String apiVersion;

    public WhatsAppClient(RestClient.Builder builder) {

        this.restClient = builder
                .baseUrl("https://graph.facebook.com")
                .build();
    }

    private String enviar(
            String phoneNumberId,
            Map<String, Object> body
    ) {
        JsonNode resposta = restClient.post()
                .uri(
                        "/{version}/{phoneId}/messages",
                        apiVersion,
                        phoneNumberId
                )
                .header("Authorization", "Bearer " + token)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        String mensagemId = resposta == null
                ? ""
                : resposta.path("messages")
                .path(0)
                .path("id")
                .asText("");

        if (mensagemId.isBlank()) {
            throw new IllegalStateException(
                    "A resposta da Meta não contém o identificador da mensagem."
            );
        }

        return mensagemId;
    }

    public String enviarTexto(String phoneNumberId, String numero, String texto) {

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", numero,
                "type", "text",
                "text", Map.of(
                        "body", texto
                )
        );

        return enviar(phoneNumberId, body);
    }

    public void enviarMeusHorarios(String phoneNumberId, String numero, List<Agendamento> agendamentos) {
        if (agendamentos.isEmpty()) {
            enviarTextoAposCommit(phoneNumberId, numero,
                    "Você não tem agendamentos futuros nesta barbearia.");
            return;
        }

        StringBuilder mensagem = new StringBuilder("📅 Seus próximos agendamentos:\n\n");
        for (int i = 0; i < agendamentos.size(); i++) {
            Agendamento agendamento = agendamentos.get(i);
            String item = """
                    %d. %s
                    Data: %s
                    Horário: %s às %s

                    """.formatted(i + 1, agendamento.getServico().getNome(),
                    agendamento.getInicio().format(FORMATO_DATA),
                    agendamento.getInicio().format(FORMATO_HORA),
                    agendamento.calcularFim().format(FORMATO_HORA));

            // Divide listas longas sem esconder reservas nem cortar um item pela metade.
            if (mensagem.length() + item.length() > LIMITE_TEXTO) {
                enviarTexto(phoneNumberId, numero, mensagem.toString().stripTrailing());
                mensagem = new StringBuilder("📅 Seus próximos agendamentos (continuação):\n\n");
            }
            mensagem.append(item);
        }
        enviarTextoAposCommit(phoneNumberId, numero, mensagem.toString().stripTrailing());
    }

    public void enviarOpcoesCancelamento(String phoneNumberId, String numero, List<Agendamento> agendamentos) {
        if (agendamentos.isEmpty()) {
            enviarTextoAposCommit(phoneNumberId, numero,
                    "Você não tem agendamentos futuros para cancelar nesta barbearia.");
            return;
        }

        int totalListas = (agendamentos.size() + LIMITE_OPCOES_LISTA - 1) / LIMITE_OPCOES_LISTA;
        for (int inicio = 0; inicio < agendamentos.size(); inicio += LIMITE_OPCOES_LISTA) {
            int fim = Math.min(inicio + LIMITE_OPCOES_LISTA, agendamentos.size());
            List<Map<String, Object>> linhas = agendamentos.subList(inicio, fim).stream()
                    .map(a -> Map.<String, Object>of(
                            "id", "ESCOLHER_CANCELAMENTO_" + a.getId(),
                            "title", a.getInicio().format(FORMATO_DATA) + " às "
                                    + a.getInicio().format(FORMATO_HORA),
                            "description", descricaoServico(a.getServico().getNome())))
                    .toList();
            String texto = "Escolha o agendamento que deseja cancelar. Você ainda vai confirmar a escolha.";
            if (totalListas > 1) {
                texto += " Lista " + (inicio / LIMITE_OPCOES_LISTA + 1) + " de " + totalListas + ".";
            }
            enviarAposCommit(phoneNumberId, Map.of(
                    "messaging_product", "whatsapp", "to", numero, "type", "interactive",
                    "interactive", Map.of(
                            "type", "list", "body", Map.of("text", texto),
                            "action", Map.of(
                                    "button", "Escolher reserva",
                                    "sections", List.of(Map.of(
                                            "title", "Seus agendamentos", "rows", linhas))))));
        }
    }

    public void enviarConfirmacaoCancelamento(String phoneNumberId, String numero, Agendamento agendamento) {
        String mensagem = """
                Deseja cancelar este agendamento?

                Serviço: %s
                Data: %s
                Horário: %s às %s
                """.formatted(agendamento.getServico().getNome(),
                agendamento.getInicio().format(FORMATO_DATA),
                agendamento.getInicio().format(FORMATO_HORA),
                agendamento.calcularFim().format(FORMATO_HORA));

        // Cada botão carrega o ID exato da reserva mostrada nesta mensagem.
        enviarAposCommit(phoneNumberId, Map.of(
                "messaging_product", "whatsapp", "to", numero, "type", "interactive",
                "interactive", Map.of(
                        "type", "button", "body", Map.of("text", mensagem),
                        "action", Map.of("buttons", List.of(
                                botao("CONFIRMAR_CANCELAMENTO_" + agendamento.getId(), "Sim, cancelar"),
                                botao("MANTER_AGENDAMENTO_" + agendamento.getId(), "Manter horário"))))));
    }

    private String descricaoServico(String nome) {
        if (nome == null || nome.isBlank()) {
            return "Serviço";
        }
        if (nome.length() <= 72) {
            return nome;
        }
        int fim = 69;
        if (Character.isHighSurrogate(nome.charAt(fim - 1))) {
            fim--; // Não corta um emoji entre as duas partes do par UTF-16.
        }
        return nome.substring(0, fim) + "...";
    }

    public void enviarMenuPrincipal(String phoneNumberId, String numero) {

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", numero,
                "type", "interactive",

                "interactive", Map.of(
                        "type", "button",

                        "body", Map.of(
                                "text",
                                "Olá! O que deseja fazer?"
                        ),

                        "action", Map.of(
                                "buttons", List.of(

                                        botao(
                                                "AGENDAR",
                                                "Agendar horário"
                                        ),

                                        botao(
                                                "CONSULTAR",
                                                "Meus horários"
                                        ),

                                        botao(
                                                "CANCELAR",
                                                "Cancelar"
                                        )
                                )
                        )
                )
        );

        enviarAposCommit(phoneNumberId, body);
    }

    private Map<String, Object> botao(String id, String titulo) {

        return Map.of(
                "type", "reply",
                "reply", Map.of(
                        "id", id,
                        "title", titulo
                )
        );
    }

    public void enviarServicos(
            String phoneNumberId,
            String numero,
            List<Servico> servicos
    ) {
        if (servicos.isEmpty()) {
            enviarTextoAposCommit(
                    phoneNumberId,
                    numero,
                    "Ainda não há serviços disponíveis para agendamento."
            );
            return;
        }

        NumberFormat moeda =
                NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));

        int totalListas =
                (servicos.size() + LIMITE_OPCOES_LISTA - 1)
                        / LIMITE_OPCOES_LISTA;

        for (int inicio = 0;
             inicio < servicos.size();
             inicio += LIMITE_OPCOES_LISTA) {

            int fim = Math.min(
                    inicio + LIMITE_OPCOES_LISTA,
                    servicos.size()
            );

            List<Map<String, Object>> linhas =
                    servicos.subList(inicio, fim)
                            .stream()
                            .map(servico -> {
                                String preco = servico.getPreco() == null
                                        ? "Preço a consultar"
                                        : moeda.format(servico.getPreco());

                                String duracao = servico.getDuracaoMinutos() == null
                                        ? ""
                                        : " • " + servico.getDuracaoMinutos() + " min";

                                return Map.<String, Object>of(
                                        "id", "SERVICO_" + servico.getId(),
                                        "title", tituloServico(servico.getNome()),
                                        "description", descricaoServico(preco + duracao)
                                );
                            })
                            .toList();

            String texto = "✂️ Escolha o serviço:";

            if (totalListas > 1) {
                int numeroLista = inicio / LIMITE_OPCOES_LISTA + 1;
                texto += " Lista " + numeroLista + " de " + totalListas + ".";
            }

            Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "to", numero,
                    "type", "interactive",
                    "interactive", Map.of(
                            "type", "list",
                            "body", Map.of("text", texto),
                            "action", Map.of(
                                    "button", "Ver serviços",
                                    "sections", List.of(
                                            Map.of(
                                                    "title", "Serviços disponíveis",
                                                    "rows", linhas
                                            )
                                    )
                            )
                    )
            );

            enviarAposCommit(phoneNumberId, body);
        }
    }

    public void enviarDatas(String phoneNumberId, String numero) {

        var hoje = java.time.LocalDate.now();

        List<Map<String, Object>> botoes = List.of(

                botao(
                        "DATA_" + hoje,
                        "Hoje"
                ),

                botao(
                        "DATA_" + hoje.plusDays(1),
                        "Amanhã"
                ),

                botao(
                        "DATA_" + hoje.plusDays(2),
                        "Depois de amanhã"
                )
        );

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", numero,
                "type", "interactive",

                "interactive", Map.of(
                        "type", "button",

                        "body", Map.of(
                                "text",
                                "📅 Escolha o dia:"
                        ),

                        "action", Map.of(
                                "buttons", botoes
                        )
                )
        );

        enviarAposCommit(phoneNumberId, body);
    }

    public void enviarHorarios(
            String phoneNumberId,
            String numero,
            List<LocalTime> horarios
    ) {
        enviarHorarios(phoneNumberId, numero, horarios, 0);
    }

    public void enviarHorarios(
            String phoneNumberId,
            String numero,
            List<LocalTime> horarios,
            int paginaSolicitada
    ) {
        if (horarios.isEmpty()) {
            enviarTextoAposCommit(phoneNumberId, numero,
                    "Não há horários disponíveis nesse dia. Escolha outra data.");
            enviarDatas(phoneNumberId, numero);
            return;
        }

        int tamanhoPagina = 8;
        int totalPaginas = (horarios.size() + tamanhoPagina - 1)
                / tamanhoPagina;

        int pagina = Math.max(
                0,
                Math.min(paginaSolicitada, totalPaginas - 1)
        );

        int inicio = pagina * tamanhoPagina;
        int fim = Math.min(inicio + tamanhoPagina, horarios.size());

        List<Map<String, Object>> linhas = new ArrayList<>();

        for (LocalTime horario : horarios.subList(inicio, fim)) {
            linhas.add(Map.of(
                    "id", "HORA_" + horario,
                    "title", horario.format(FORMATO_HORA)
            ));
        }

        if (pagina > 0) {
            linhas.add(Map.of(
                    "id", "PAGINA_HORARIOS_" + (pagina - 1),
                    "title", "Horários anteriores"
            ));
        }

        if (pagina < totalPaginas - 1) {
            linhas.add(Map.of(
                    "id", "PAGINA_HORARIOS_" + (pagina + 1),
                    "title", "Ver mais horários"
            ));
        }

        String mensagem = """
            Escolha um horário disponível.
            Página %d de %d.
            """.formatted(pagina + 1, totalPaginas);

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", numero,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "list",
                        "body", Map.of("text", mensagem),
                        "action", Map.of(
                                "button", "Ver horários",
                                "sections", List.of(
                                        Map.of(
                                                "title", "Horários disponíveis",
                                                "rows", linhas
                                        )
                                )
                        )
                )
        );

        enviarAposCommit(phoneNumberId, body);
    }

    public void enviarConfirmacao(String phoneNumberId, String numero, SessaoConversa sessao) {

        NumberFormat moeda = NumberFormat.getCurrencyInstance(
                Locale.forLanguageTag("pt-BR")
        );

        String preco = sessao.getPrecoServicoNaConfirmacao() == null
                ? "A consultar"
                : moeda.format(sessao.getPrecoServicoNaConfirmacao());

        Integer duracao = sessao.getDuracaoServicoNaConfirmacao();

        String duracaoTexto = duracao == null
                ? "Não informada"
                : duracao + " minutos";

        String mensagem = """
        Confirme seu agendamento 💈

        Serviço: %s
        Preço: %s
        Duração: %s
        Data: %s
        Horário: %s
        """.formatted(
                sessao.getServicoSelecionado().getNome(),
                preco,
                duracaoTexto,
                sessao.getDataSelecionada().format(FORMATO_DATA),
                sessao.getHorarioSelecionado().format(FORMATO_HORA)
        );

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", numero,
                "type", "interactive",

                "interactive", Map.of(
                        "type", "button",

                        "body", Map.of(
                                "text",
                                mensagem
                        ),

                        "action", Map.of(
                                "buttons", List.of(

                                        botao(
                                                "CONFIRMAR_" + sessao.getConfirmacaoId(),
                                                "Confirmar"
                                        ),

                                        botao(
                                                "VOLTAR_" + sessao.getConfirmacaoId(),
                                                "Voltar"
                                        )
                                )
                        )
                )
        );

        enviarAposCommit(phoneNumberId, body);
    }

    public String enviarTemplate(
            String phoneNumberId,
            String numero,
            String nomeTemplate,
            List<String> valores
    ) {
        List<Map<String, Object>> parametros = valores.stream()
                .map(valor -> Map.<String, Object>of(
                        "type", "text",
                        "text", valor
                ))
                .toList();

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", numero,
                "type", "template",
                "template", Map.of(
                        "name", nomeTemplate,
                        "language", Map.of("code", "pt_BR"),
                        "components", List.of(
                                Map.of(
                                        "type", "body",
                                        "parameters", parametros
                                )
                        )
                )
        );

        return enviar(phoneNumberId, body);
    }

    public void enviarMenuAdministrador(
            String phoneNumberId,
            String numero
    ) {
        List<Map<String, Object>> opcoes = List.of(
                Map.<String, Object>of(
                        "id", "ADMIN_BLOQUEAR_DIA",
                        "title", "Bloquear dia",
                        "description", "Bloquear uma data na agenda"
                ),
                Map.<String, Object>of(
                        "id", "ADMIN_LIBERAR_DIA",
                        "title", "Liberar dia",
                        "description", "Remover o bloqueio de uma data"
                ),
                Map.<String, Object>of(
                        "id", "ADMIN_VER_AGENDAMENTOS",
                        "title", "Ver agendamentos",
                        "description", "Consultar as reservas de uma data"
                ),
                Map.<String, Object>of(
                        "id", "ADMIN_CADASTRAR_SERVICO",
                        "title", "Cadastrar serviço",
                        "description", "Adicionar um serviço com nome, preço e duração"
                ),
                Map.<String, Object>of(
                        "id", "ADMIN_EDITAR_SERVICO",
                        "title", "Editar serviço",
                        "description", "Alterar nome, preço ou duração de um serviço"
                ),
                Map.<String, Object>of(
                        "id", "ADMIN_ATIVAR_SERVICO",
                        "title", "Ativar serviço",
                        "description", "Disponibilizar um serviço para novos agendamentos"
                ),
                Map.<String, Object>of(
                        "id", "ADMIN_DESATIVAR_SERVICO",
                        "title", "Desativar serviço",
                        "description", "Retirar um serviço dos novos agendamentos"
                )
        );

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", numero,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "list",
                        "body", Map.of(
                                "text",
                                "Gestão da barbearia 💈\nO que deseja fazer?"
                        ),
                        "action", Map.of(
                                "button", "Ver opções",
                                "sections", List.of(
                                        Map.of(
                                                "title", "Administração",
                                                "rows", opcoes
                                        )
                                )
                        )
                )
        );

        enviarAposCommit(phoneNumberId, body);
    }

    public void enviarConfirmacaoBloqueio(
            String phoneNumberId,
            String numero,
            UUID previaId,
            LocalDate data,
            int quantidadeAfetados
    ) {
        String mensagem = """
            Confirma o bloqueio de %s?

            Agendamentos que serão cancelados: %d.

            Ao confirmar, o dia será bloqueado e os avisos
            aos clientes serão preparados para envio.

            Esta confirmação vale por 10 minutos
            a partir da criação da prévia.
            """.formatted(
                data.format(FORMATO_DATA),
                quantidadeAfetados
        );

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", numero,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "button",
                        "body", Map.of("text", mensagem),
                        "action", Map.of(
                                "buttons", List.of(
                                        botao(
                                                "ADMIN_CONFIRMAR_BLOQUEIO_" + previaId,
                                                "Confirmar bloqueio"
                                        )
                                )
                        )
                )
        );

        enviarAposCommit(phoneNumberId, body);
    }

    public void enviarAgendaDoDia(
            String phoneNumberId,
            String numeroAdministrador,
            LocalDate data,
            List<Agendamento> agendamentos
    ) {
        String dataFormatada = data.format(FORMATO_DATA);

        if (agendamentos.isEmpty()) {
            enviarTextoAposCommit(
                    phoneNumberId,
                    numeroAdministrador,
                    "Não há agendamentos confirmados para " + dataFormatada + "."
            );
            return;
        }

        String cabecalho = "Agenda de " + dataFormatada
                + "\nAgendamentos confirmados: " + agendamentos.size()
                + "\n\n";

        StringBuilder mensagem = new StringBuilder(cabecalho);

        for (Agendamento agendamento : agendamentos) {
            String item = """
                Reserva #%d
                Cliente: %.100s
                Serviço: %.100s
                Horário: %s às %s

                """.formatted(
                    agendamento.getId(),
                    agendamento.getCliente().getNomeCompleto(),
                    agendamento.getServico().getNome(),
                    agendamento.getInicio().format(FORMATO_HORA),
                    agendamento.calcularFim().format(FORMATO_HORA)
            );

            if (mensagem.length() + item.length() > 3500) {
                enviarTextoAposCommit(
                        phoneNumberId,
                        numeroAdministrador,
                        mensagem.toString()
                );

                mensagem = new StringBuilder(
                        "Agenda de " + dataFormatada + " — continuação\n\n"
                );
            }

            mensagem.append(item);
        }

        enviarTextoAposCommit(
                phoneNumberId,
                numeroAdministrador,
                mensagem.toString()
        );
    }

    public void enviarTextoAposCommit(
            String phoneNumberId,
            String numero,
            String texto
    ) {
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", numero,
                "type", "text",
                "text", Map.of("body", texto)
        );

        enviarAposCommit(phoneNumberId, body);
    }

    private void enviarAposCommit(
            String phoneNumberId,
            Map<String, Object> body
    ) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            enviar(phoneNumberId, body);
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "A transação não permite registrar o envio após o commit."
            );
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            enviar(phoneNumberId, body);
                        } catch (RuntimeException e) {
                            log.error(
                                    "Falha ao enviar resposta após o commit. "
                                            + "As alterações no banco foram mantidas.",
                                    e
                            );
                        }
                    }
                }
        );
    }

    private String tituloServico(String nome) {
        if (nome == null || nome.isBlank()) {
            return "Serviço";
        }

        String titulo = nome.strip();

        if (titulo.length() <= 24) {
            return titulo;
        }

        int fim = 21;

        if (Character.isHighSurrogate(titulo.charAt(fim - 1))) {
            fim--;
        }

        return titulo.substring(0, fim) + "...";
    }

    public void enviarServicosParaEdicao(
            String phoneNumberId,
            String numero,
            List<Servico> servicos
    ) {
        if (servicos.isEmpty()) {
            enviarTextoAposCommit(
                    phoneNumberId,
                    numero,
                    "Você ainda não tem serviços cadastrados para editar."
            );
            return;
        }

        NumberFormat moeda = NumberFormat.getCurrencyInstance(
                Locale.forLanguageTag("pt-BR")
        );

        int totalListas =
                (servicos.size() + LIMITE_OPCOES_LISTA - 1)
                        / LIMITE_OPCOES_LISTA;

        for (int inicio = 0;
             inicio < servicos.size();
             inicio += LIMITE_OPCOES_LISTA) {

            int fim = Math.min(
                    inicio + LIMITE_OPCOES_LISTA,
                    servicos.size()
            );

            List<Map<String, Object>> linhas =
                    servicos.subList(inicio, fim)
                            .stream()
                            .map(servico -> {
                                String preco = servico.getPreco() == null
                                        ? "Preço a consultar"
                                        : moeda.format(servico.getPreco());

                                String duracao =
                                        servico.getDuracaoMinutos() == null
                                                ? ""
                                                : " • "
                                                + servico.getDuracaoMinutos()
                                                + " min";

                                return Map.<String, Object>of(
                                        "id",
                                        "ADMIN_EDITAR_SERVICO_" + servico.getId(),
                                        "title",
                                        tituloServico(servico.getNome()),
                                        "description",
                                        descricaoServico(preco + duracao)
                                );
                            })
                            .toList();

            String texto = "Escolha o serviço que deseja editar.";

            if (totalListas > 1) {
                int numeroLista = inicio / LIMITE_OPCOES_LISTA + 1;

                texto += " Lista " + numeroLista
                        + " de " + totalListas + ".";
            }

            Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "to", numero,
                    "type", "interactive",
                    "interactive", Map.of(
                            "type", "list",
                            "body", Map.of(
                                    "text", texto
                            ),
                            "action", Map.of(
                                    "button", "Escolher serviço",
                                    "sections", List.of(
                                            Map.of(
                                                    "title", "Seus serviços",
                                                    "rows", linhas
                                            )
                                    )
                            )
                    )
            );

            enviarAposCommit(phoneNumberId, body);
        }
    }

    public void enviarServicosParaAlterarStatus(
            String phoneNumberId,
            String numero,
            List<Servico> servicos,
            boolean ativar
    ) {
        List<Servico> candidatos = servicos.stream()
                .filter(servico -> servico.isAtivo() != ativar)
                .toList();

        if (candidatos.isEmpty()) {
            enviarTextoAposCommit(
                    phoneNumberId,
                    numero,
                    ativar
                            ? "Não há serviços desativados para ativar."
                            : "Não há serviços ativos para desativar."
            );
            return;
        }

        String prefixo = ativar
                ? "ADMIN_ATIVAR_SERVICO_"
                : "ADMIN_DESATIVAR_SERVICO_";

        NumberFormat moeda = NumberFormat.getCurrencyInstance(
                Locale.forLanguageTag("pt-BR")
        );

        int totalListas =
                (candidatos.size() + LIMITE_OPCOES_LISTA - 1)
                        / LIMITE_OPCOES_LISTA;

        for (int inicio = 0;
             inicio < candidatos.size();
             inicio += LIMITE_OPCOES_LISTA) {

            int fim = Math.min(
                    inicio + LIMITE_OPCOES_LISTA,
                    candidatos.size()
            );

            List<Map<String, Object>> linhas =
                    candidatos.subList(inicio, fim)
                            .stream()
                            .map(servico -> {
                                String preco = servico.getPreco() == null
                                        ? "Preço a consultar"
                                        : moeda.format(servico.getPreco());

                                String duracao =
                                        servico.getDuracaoMinutos() == null
                                                ? ""
                                                : " • "
                                                + servico.getDuracaoMinutos()
                                                + " min";

                                return Map.<String, Object>of(
                                        "id", prefixo + servico.getId(),
                                        "title", tituloServico(servico.getNome()),
                                        "description", descricaoServico(preco + duracao)
                                );
                            })
                            .toList();

            String texto = ativar
                    ? "Selecione o serviço para ativar. Ele ficará disponível para novos agendamentos."
                    : "Selecione o serviço para desativar. As reservas já confirmadas serão mantidas.";

            if (totalListas > 1) {
                int numeroLista = inicio / LIMITE_OPCOES_LISTA + 1;

                texto += " Lista " + numeroLista
                        + " de " + totalListas + ".";
            }

            Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "to", numero,
                    "type", "interactive",
                    "interactive", Map.of(
                            "type", "list",
                            "body", Map.of("text", texto),
                            "action", Map.of(
                                    "button", "Escolher serviço",
                                    "sections", List.of(
                                            Map.of(
                                                    "title", ativar
                                                            ? "Serviços desativados"
                                                            : "Serviços ativos",
                                                    "rows", linhas
                                            )
                                    )
                            )
                    )
            );

            enviarAposCommit(phoneNumberId, body);
        }
    }
}

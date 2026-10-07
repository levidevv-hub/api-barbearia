package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.domain.Cliente;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class ConversaService {

    private final IBarbeiroRepository barbeiroRepository;
    private final ClienteService clienteService;
    private final ConversaAgendamentoService agendamentoService;
    private final ConversaAdminService adminService;
    private final AutorizacaoBarbeiroService autorizacaoBarbeiroService;
    private final CentralWhatsappService centralWhatsappService;
    private final ConversaCentralService conversaCentralService;

    public void processar(JsonNode payload) {
        JsonNode value = payload
                .path("entry")
                .path(0)
                .path("changes")
                .path(0)
                .path("value");

        JsonNode messages = value.path("messages");

        if (!messages.isArray() || messages.isEmpty()) {
            return;
        }

        JsonNode mensagem = messages.get(0);

        String telefoneCliente = mensagem.path("from").asText();
        String phoneNumberId = value.path("metadata")
                .path("phone_number_id")
                .asText();

        // A central é administrativa, inclusive quando seu próprio robô está pausado.
        if (centralWhatsappService.ehCentral(phoneNumberId)) {
            conversaCentralService.processar(phoneNumberId, telefoneCliente, mensagem);
            return;
        }

        // Mensagens administrativas enviadas pela central não são clientes.
        // Evita que a linha do barbeiro responda à central criando um ciclo.
        if (centralWhatsappService.ehRemetenteCentral(telefoneCliente)) return;

        String nomeCliente = value.path("contacts")
                .path(0)
                .path("profile")
                .path("name")
                .asText("Cliente");

        Barbeiro barbeiro = barbeiroRepository
                .findByWhatsappPhoneNumberId(phoneNumberId)
                .orElseThrow();

        // O administrador precisa continuar acessando Minha agenda para reativar.
        // Mensagens ignoradas são concluídas pelo processamento do webhook,
        // sem cadastrar clientes ou avançar sessões de agendamento.
        if (!barbeiro.isRoboAtivo()
                && !autorizacaoBarbeiroService.podeAdministrar(
                        barbeiro.getId(), telefoneCliente)) {
            return;
        }

        Cliente cliente = clienteService.buscarOuCriar(
                nomeCliente,
                telefoneCliente
        );

        String tipo = mensagem.path("type").asText();

        if ("text".equals(tipo)) {
            processarTexto(
                    mensagem,
                    barbeiro,
                    cliente,
                    telefoneCliente
            );
            return;
        }

        if ("interactive".equals(tipo)) {
            processarInteracao(
                    mensagem,
                    barbeiro,
                    cliente,
                    telefoneCliente
            );
        }
    }

    private void processarTexto(
            JsonNode mensagem,
            Barbeiro barbeiro,
            Cliente cliente,
            String telefone
    ) {
        String texto = mensagem.path("text")
                .path("body")
                .asText("")
                .strip();

        if (adminService.processarTexto(texto, barbeiro, telefone)) {
            return;
        }

        if (!barbeiro.isRoboAtivo()) {
            return;
        }

        agendamentoService.iniciar(
                barbeiro,
                cliente,
                telefone
        );
    }

    private void processarInteracao(
            JsonNode mensagem,
            Barbeiro barbeiro,
            Cliente cliente,
            String telefone
    ) {
        JsonNode interactive = mensagem.path("interactive");

        String id = interactive.has("button_reply")
                ? interactive.path("button_reply").path("id").asText()
                : interactive.path("list_reply").path("id").asText();

        if (adminService.processarInteracao(
                id,
                barbeiro,
                telefone
        )) {
            return;
        }

        if (!barbeiro.isRoboAtivo()) {
            return;
        }

        agendamentoService.processarInteracao(
                id,
                barbeiro,
                cliente,
                telefone
        );
    }
}

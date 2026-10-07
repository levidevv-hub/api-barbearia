package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.*;
import com.guilhermelevi.barbearia.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ConversaCentralService {
    private final IBarbeiroRepository barbeiros;
    private final ISessaoCentralWhatsappRepository sessoes;
    private final AutorizacaoBarbeiroService autorizacao;
    private final ConversaAdminService admin;
    private final WhatsAppClient whatsapp;

    public void processar(String linhaCentral, String remetente, JsonNode mensagem) {
        if (!remetente.matches("[1-9][0-9]{7,14}")) return;
        var permitidos = barbeiros.findByNumeroWhatsAppAdministradorOrderByIdAsc(remetente);
        if (permitidos.isEmpty()) {
            resposta(linhaCentral, remetente, "Seu número não está cadastrado como administrador de uma barbearia.");
            return;
        }
        String tipo = mensagem.path("type").asText();
        if ("text".equals(tipo)) {
            texto(linhaCentral, remetente, mensagem.path("text").path("body").asText("").strip(), permitidos);
        } else if ("interactive".equals(tipo)) {
            var interacao = mensagem.path("interactive");
            String id = interacao.has("button_reply") ? interacao.path("button_reply").path("id").asText()
                    : interacao.path("list_reply").path("id").asText();
            interacao(linhaCentral, remetente, id, permitidos);
        }
    }

    private void texto(String linha, String remetente, String texto, List<Barbeiro> permitidos) {
        if ("minha agenda".equalsIgnoreCase(texto) || "trocar barbearia".equalsIgnoreCase(texto)) {
            if (permitidos.size() > 1) { escolher(linha, remetente, permitidos, 0); return; }
            selecionar(linha, remetente, permitidos.get(0));
            return;
        }
        var sessao = sessoes.findById(chave(linha, remetente)).orElse(null);
        var alvo = sessao == null ? Optional.<Barbeiro>empty() : permitido(permitidos, sessao.getBarbeiroId());
        if (alvo.isEmpty()) {
            if (permitidos.size() > 1) { escolher(linha, remetente, permitidos, 0); return; }
            selecionar(linha, remetente, permitidos.get(0));
            return;
        }
        Barbeiro barbeiro = alvo.get();
        if (!autorizacao.podeAdministrar(barbeiro.getId(), remetente)) { negar(linha, remetente); return; }
        try (var escopo = RespostaCentralWhatsapp.abrir(linha, barbeiro.getId())) {
            if (!admin.processarTexto(texto, barbeiro, remetente)) {
                whatsapp.enviarTextoAposCommit(linha, remetente,
                        "Envie Minha agenda para ver as opções ou Trocar barbearia para selecionar outra agenda.");
            }
        }
    }

    private void interacao(String linha, String remetente, String id, List<Barbeiro> permitidos) {
        try {
            if (id.startsWith("CENTRAL_PAGINA_")) {
                escolher(linha, remetente, permitidos, Integer.parseInt(id.substring("CENTRAL_PAGINA_".length())));
                return;
            }
            if (id.startsWith("CENTRAL_SELECIONAR_")) {
                long alvo = Long.parseLong(id.substring("CENTRAL_SELECIONAR_".length()));
                var barbeiro = permitido(permitidos, alvo);
                if (barbeiro.isEmpty() || !autorizacao.podeAdministrar(alvo, remetente)) { negar(linha, remetente); return; }
                selecionar(linha, remetente, barbeiro.get());
                return;
            }
            if (!id.matches("CENTRAL_[1-9][0-9]*_ADMIN_.+")) { negar(linha, remetente); return; }
            int separador = id.indexOf('_', "CENTRAL_".length());
            long alvo = Long.parseLong(id.substring("CENTRAL_".length(), separador));
            String acao = id.substring(separador + 1);
            var barbeiro = permitido(permitidos, alvo);
            var sessao = sessoes.findById(chave(linha, remetente)).orElse(null);
            if (barbeiro.isEmpty() || sessao == null || !Objects.equals(sessao.getBarbeiroId(), alvo)
                    || !autorizacao.podeAdministrar(alvo, remetente)) { negar(linha, remetente); return; }
            try (var escopo = RespostaCentralWhatsapp.abrir(linha, alvo)) {
                admin.processarInteracao(acao, barbeiro.get(), remetente);
            }
        } catch (NumberFormatException e) {
            negar(linha, remetente);
        }
    }

    private void selecionar(String linha, String remetente, Barbeiro barbeiro) {
        if (!autorizacao.podeAdministrar(barbeiro.getId(), remetente)) { negar(linha, remetente); return; }
        var sessao = sessoes.findById(chave(linha, remetente))
                .orElseGet(() -> new SessaoCentralWhatsapp(chave(linha, remetente), barbeiro.getId()));
        sessao.selecionar(barbeiro.getId());
        sessoes.save(sessao);
        try (var escopo = RespostaCentralWhatsapp.abrir(linha, barbeiro.getId())) {
            whatsapp.enviarTextoAposCommit(linha, remetente, "Administrando: " + barbeiro.getNome());
            admin.processarTexto("Minha agenda", barbeiro, remetente);
        }
    }

    private void escolher(String linha, String remetente, List<Barbeiro> permitidos, int pagina) {
        int total = (permitidos.size() + 7) / 8;
        if (pagina < 0 || pagina >= total) { negar(linha, remetente); return; }
        // Invalida os botões do contexto anterior enquanto uma nova agenda é escolhida.
        sessoes.findById(chave(linha, remetente)).ifPresent(s -> { s.selecionar(null); sessoes.save(s); });
        whatsapp.enviarSelecaoBarbeariasCentral(linha, remetente, permitidos, pagina);
    }

    private Optional<Barbeiro> permitido(List<Barbeiro> permitidos, Long id) {
        return permitidos.stream().filter(b -> Objects.equals(b.getId(), id)).findFirst();
    }
    private String chave(String linha, String remetente) { return linha + ":" + remetente; }
    private void negar(String linha, String remetente) {
        resposta(linha, remetente, "Esta opção não pertence à agenda selecionada ou você não tem mais permissão. Envie Minha agenda.");
    }
    private void resposta(String linha, String remetente, String texto) { whatsapp.enviarTextoAposCommit(linha, remetente, texto); }
}

package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.WhatsAppClient;
import com.guilhermelevi.barbearia.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.*;

@Service @RequiredArgsConstructor
public class AtendimentoHumanoService {
    private final IAtendimentoHumanoRepository atendimentos;
    private final IMensagemAtendimentoRepository mensagens;
    private final IBarbeiroRepository barbeiros;
    private final IExpedienteSemanalRepository expedientes;
    private final IPeriodoExpedienteRepository periodos;
    private final IBloqueioDataRepository bloqueios;
    private final WhatsAppClient whatsapp;
    private final CentralWhatsappService central;
    @Value("${whatsapp.atendimento.template:atendimento_humano_barbeiro}") private String template;
    private static final ZoneId ZONA = ZoneId.of("America/Fortaleza");

    /** Toda mutação usa a mesma trava por estabelecimento, inclusive a criação da primeira conversa. */
    @Transactional
    public boolean interceptar(Barbeiro barbeiro, Cliente cliente, JsonNode mensagem) {
        String numero = cliente.getNumeroTelefone();
        String tipo = mensagem.path("type").asText();
        String texto = mensagem.path("text").path("body").asText("");
        var interacao = mensagem.path("interactive");
        String id = interacao.path("button_reply").path("id").asText(
                interacao.path("list_reply").path("id").asText(""));
        boolean solicitacao = "FALAR_ATENDENTE".equals(id) || solicitaAtendente(texto);
        barbeiros.buscarParaAgendar(barbeiro.getId()).orElseThrow();
        var existente = atendimentos.findByBarbeiroIdAndNumeroCliente(barbeiro.getId(), numero);
        if (!solicitacao && (existente.isEmpty() || existente.get().getEstado() == AtendimentoHumano.Estado.AUTOMATICO)) return false;
        var a = existente.orElseGet(AtendimentoHumano::new);
        boolean novaSolicitacao = a.getEstado() == AtendimentoHumano.Estado.AUTOMATICO;
        a.setBarbeiro(barbeiro); a.setNumeroCliente(numero); a.setNomeCliente(cliente.getNomeCompleto());
        // Usa timestamp da Meta; um webhook atrasado não reabre artificialmente a janela de 24 horas.
        long timestamp = mensagem.path("timestamp").asLong(0);
        if (timestamp > 0) {
            LocalDateTime recebido = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZONA);
            if (recebido.isAfter(agora())) recebido = agora();
            if (a.getUltimaMensagemClienteEm() == null || recebido.isAfter(a.getUltimaMensagemClienteEm())) a.setUltimaMensagemClienteEm(recebido);
        }
        if (novaSolicitacao) {
            a.setEstado(AtendimentoHumano.Estado.AGUARDANDO); a.setResponsavel(null); a.setSolicitadoEm(agora());
            a.setAvisoMensagemId(null); a.setAvisoPhoneNumberId(null); a.setErroAviso(null);
        }
        atendimentos.save(a);
        String conteudo = "text".equals(tipo) ? texto : "interactive".equals(tipo) ? "[Opção] " + id
                : "[" + tipo + "] Mídia recebida. A visualização de anexos ainda não está disponível neste painel.";
        registrar(a, "CLIENTE", conteudo, mensagem.path("id").asText(null));
        if (novaSolicitacao) {
            whatsapp.enviarTextoAposCommit(barbeiro.getWhatsappPhoneNumberId(), numero,
                    "Solicitei seu atendimento. Você pode deixar sua dúvida aqui. O atendimento automático está pausado." + avisoExpediente(barbeiro.getId()));
            // Aviso persistido na conversa; o scheduler tenta somente depois do commit.
        }
        return true;
    }

    public static boolean solicitaAtendente(String texto) {
        String normal = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").strip();
        return Set.of("atendente", "falar com atendente", "falar com um atendente", "falar com alguem",
                "quero falar com atendente", "quero falar com um atendente", "atendimento humano").contains(normal);
    }

    private String avisoExpediente(Long barbeiroId) {
        LocalDateTime agora = agora();
        for (int d = 0; d <= 14; d++) {
            LocalDate data = agora.toLocalDate().plusDays(d);
            if (bloqueios.existsByBarbeiroIdAndData(barbeiroId, data)) continue;
            var expediente = expedientes.findByBarbeiroIdAndDiaSemana(barbeiroId, data.getDayOfWeek());
            if (expediente.isEmpty() || !expediente.get().isAberto()) continue;
            for (var p : periodos.findByExpedienteSemanalIdOrderByInicioAsc(expediente.get().getId())) {
                if (d == 0 && !agora.toLocalTime().isBefore(p.getInicio()) && agora.toLocalTime().isBefore(p.getFim())) return "";
                if (data.atTime(p.getInicio()).isAfter(agora)) return " Estamos fora do expediente. Próxima abertura: "
                        + data.format(DateTimeFormatter.ofPattern("dd/MM")) + " às " + p.getInicio().format(DateTimeFormatter.ofPattern("HH:mm")) + ".";
            }
        }
        return " O responsável responderá assim que estiver disponível; não há próximo expediente cadastrado nas próximas duas semanas.";
    }

    @Transactional
    public void avisar(Long barbeiroId, Long id) {
        var a = buscarComTrava(barbeiroId, id);
        if (a.getEstado() != AtendimentoHumano.Estado.AGUARDANDO || a.getAvisoMensagemId() != null || a.getErroAviso() != null) return;
        try {
            String destino = a.getBarbeiro().getNumeroWhatsAppNotificacao();
            if (destino == null || destino.isBlank()) throw new IllegalArgumentException("Cadastre o número de notificação do profissional.");
            a.setAvisoPhoneNumberId(central.linhaNotificacao(a.getBarbeiro().getWhatsappPhoneNumberId()));
            a.setAvisoMensagemId(whatsapp.enviarTemplate(a.getAvisoPhoneNumberId(), destino, template,
                    List.of(a.getNomeCliente().replaceAll("\\s+", " ").strip(), a.getNumeroCliente(),
                            a.getBarbeiro().getNome().replaceAll("\\s+", " ").strip())));
        } catch (RuntimeException e) {
            a.setErroAviso("Não foi possível enviar o aviso. Confira o número, a conexão e a aprovação do template " + template + " na conta da linha remetente.");
        }
    }

    @Transactional
    public void assumir(Long barbeiroId, Long id, String operador) {
        var a = buscarComTrava(barbeiroId, id);
        if (a.getEstado() == AtendimentoHumano.Estado.AUTOMATICO) throw conflito("Este atendimento já foi finalizado.");
        if (a.getEstado() == AtendimentoHumano.Estado.EM_ATENDIMENTO && !operador.equals(a.getResponsavel())) throw conflito("Outro atendente já assumiu esta conversa.");
        a.setEstado(AtendimentoHumano.Estado.EM_ATENDIMENTO); a.setResponsavel(operador);
    }

    @Transactional
    public void responder(Long barbeiroId, Long id, String operador, String texto) {
        var a = buscarComTrava(barbeiroId, id);
        if (a.getEstado() != AtendimentoHumano.Estado.EM_ATENDIMENTO || !operador.equals(a.getResponsavel())) throw conflito("Assuma este atendimento antes de responder.");
        validarJanela(a);
        String mensagemId = whatsapp.enviarTexto(a.getBarbeiro().getWhatsappPhoneNumberId(), a.getNumeroCliente(), texto.strip());
        registrar(a, operador, texto.strip(), mensagemId);
    }

    @Transactional
    public void finalizar(Long barbeiroId, Long id, String operador) {
        var a = buscarComTrava(barbeiroId, id);
        if (a.getEstado() == AtendimentoHumano.Estado.AUTOMATICO) return;
        if (a.getResponsavel() != null && !operador.equals(a.getResponsavel())) throw conflito("Este atendimento está com outro responsável.");
        a.setEstado(AtendimentoHumano.Estado.AUTOMATICO); a.setResponsavel(null);
        registrar(a, "SISTEMA", "Atendimento finalizado por " + operador + ". Robô liberado.", null);
        if (janelaAberta(a)) whatsapp.enviarTextoAposCommit(a.getBarbeiro().getWhatsappPhoneNumberId(), a.getNumeroCliente(),
                "Atendimento finalizado. Para voltar ao agendamento automático, envie oi. As opções anteriores continuam sujeitas à disponibilidade.");
        // Não apaga a SessaoConversa. Qualquer confirmação posterior passa novamente pelas validações da reserva.
    }

    public AtendimentoHumano buscarComTrava(Long barbeiroId, Long id) {
        barbeiros.buscarParaAgendar(barbeiroId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return atendimentos.findByIdAndBarbeiroId(id, barbeiroId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private void validarJanela(AtendimentoHumano a) {
        if (!janelaAberta(a)) throw conflito("A janela de 24 horas encerrou. Aguarde uma nova mensagem do cliente para responder pelo painel.");
    }
    public boolean janelaAberta(AtendimentoHumano a) { return a.getUltimaMensagemClienteEm() != null && a.getUltimaMensagemClienteEm().plusHours(24).isAfter(agora()); }
    private LocalDateTime agora() { return LocalDateTime.now(ZONA); }
    private ResponseStatusException conflito(String mensagem) { return new ResponseStatusException(HttpStatus.CONFLICT, mensagem); }
    private void registrar(AtendimentoHumano a, String autor, String texto, String metaId) {
        var m = new MensagemAtendimento(); m.setAtendimento(a); m.setAutor(autor); m.setTexto(texto);
        m.setMensagemIdMeta(metaId); mensagens.save(m);
    }
}

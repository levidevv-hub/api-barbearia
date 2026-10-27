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
    private static final Duration DURACAO_PAUSA = Duration.ofHours(1);

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
        // Uma pausa vencida nao intercepta a proxima mensagem: o bot volta normalmente.
        if (existente.isPresent() && pausaExpirada(existente.get())) {
            reativar(existente.get(), "Pausa de 1 hora encerrada. Robô reativado.");
        }
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
            a.setPausaAte(agora().plus(DURACAO_PAUSA));
            a.setAvisoMensagemId(null); a.setAvisoPhoneNumberId(null); a.setErroAviso(null);
        }
        atendimentos.save(a);
        String conteudo = "text".equals(tipo) ? texto : "interactive".equals(tipo) ? "[Opção] " + id
                : "[" + tipo + "] Mídia recebida. A visualização de anexos ainda não está disponível neste painel.";
        registrar(a, "CLIENTE", conteudo, mensagem.path("id").asText(null));
        if (novaSolicitacao) {
            whatsapp.enviarTextoAposCommit(barbeiro.getWhatsappPhoneNumberId(), numero,
                    "Solicitei seu atendimento. Você pode deixar sua dúvida aqui. O atendimento automático ficará pausado por 1 hora." + avisoExpediente(barbeiro.getId()));
            // Aviso persistido na conversa; o scheduler tenta somente depois do commit.
        }
        return true;
    }

    @Transactional(readOnly=true)
    public boolean estaAtivo(Long barbeiroId, String numero) {
        return atendimentos.findByBarbeiroIdAndNumeroCliente(barbeiroId, numero)
                .map(a -> a.getEstado() != AtendimentoHumano.Estado.AUTOMATICO && !pausaExpirada(a)).orElse(false);
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
        if (a.getEstado() != AtendimentoHumano.Estado.AGUARDANDO || pausaExpirada(a) || a.getAvisoMensagemId() != null || a.getErroAviso() != null) return;
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
        if (a.getEstado() == AtendimentoHumano.Estado.AUTOMATICO || pausaExpirada(a)) throw conflito("O atendimento humano já foi encerrado.");
        if (a.getEstado() == AtendimentoHumano.Estado.EM_ATENDIMENTO && !operador.equals(a.getResponsavel())) throw conflito("Outro atendente já assumiu esta conversa.");
        a.setEstado(AtendimentoHumano.Estado.EM_ATENDIMENTO); a.setResponsavel(operador);
    }

    @Transactional
    public void responder(Long barbeiroId, Long id, String operador, String texto) {
        var a = buscarComTrava(barbeiroId, id);
        if (pausaExpirada(a)) throw conflito("A pausa de 1 hora terminou.");
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
        reativar(a, "Atendimento finalizado por " + operador + ". Robô liberado.");
        if (janelaAberta(a)) whatsapp.enviarTextoAposCommit(a.getBarbeiro().getWhatsappPhoneNumberId(), a.getNumeroCliente(),
                "Atendimento finalizado. Para voltar ao agendamento automático, envie oi. As opções anteriores continuam sujeitas à disponibilidade.");
        // Não apaga a SessaoConversa. Qualquer confirmação posterior passa novamente pelas validações da reserva.
    }

    private boolean pausaExpirada(AtendimentoHumano a) {
        return a.getEstado() != AtendimentoHumano.Estado.AUTOMATICO
                && a.getPausaAte() != null && !a.getPausaAte().isAfter(agora());
    }

    private void reativar(AtendimentoHumano a, String motivo) {
        a.setEstado(AtendimentoHumano.Estado.AUTOMATICO);
        a.setResponsavel(null);
        a.setPausaAte(null);
        registrar(a, "SISTEMA", motivo, null);
    }

    @Transactional
    public void reativarSeExpirado(Long atendimentoId) {
        var a = atendimentos.findById(atendimentoId).orElse(null);
        if (a != null && pausaExpirada(a)) {
            // Serializa com novas mensagens deste estabelecimento.
            barbeiros.buscarParaAgendar(a.getBarbeiro().getId()).orElseThrow();
            if (pausaExpirada(a)) reativar(a, "Pausa de 1 hora encerrada. Robô reativado.");
        }
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

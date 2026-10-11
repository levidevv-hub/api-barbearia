package com.guilhermelevi.barbearia.admin;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.repositories.*;
import com.guilhermelevi.barbearia.service.AtendimentoHumanoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/admin/barbeiros/{barbeiroId}/atendimentos")
public class AtendimentoHumanoController {
    private final AtendimentoHumanoService service;
    private final IAtendimentoHumanoRepository atendimentos;
    private final IMensagemAtendimentoRepository mensagens;
    private final INotificacaoPendenteRepository notificacoes;
    private final IStatusMensagemRecebidoRepository status;

    public record Resumo(Long id, String nome, String numero, String estado, String responsavel,
                         LocalDateTime solicitadoEm, boolean podeResponder, String aviso, String erroAviso) {}
    public record Mensagem(Long id, String autor, String texto, LocalDateTime criadaEm) {}
    public record Resposta(@NotBlank @Size(max=4096) String texto) {}
    public record Notificacao(Long id, String tipo, String estado, int tentativas, String erro) {}

    @GetMapping @Transactional(readOnly=true)
    public List<Resumo> listar(@PathVariable Long barbeiroId) {
        return atendimentos.findTop100ByBarbeiroIdOrderBySolicitadoEmDesc(barbeiroId).stream().map(a -> {
            String aviso = a.getErroAviso() != null ? "FALHA" : a.getAvisoMensagemId() == null ? "PENDENTE" : "ACEITA_PELA_API";
            String erro = a.getErroAviso();
            if (a.getAvisoMensagemId() != null) {
                var eventos = status.findByMensagemIdMetaAndPhoneNumberIdOrderByRecebidoEmAsc(a.getAvisoMensagemId(), a.getAvisoPhoneNumberId());
                for (var evento : eventos) {
                    if ("delivered".equals(evento.getStatus()) || "read".equals(evento.getStatus())) { aviso = "ENTREGUE"; erro = null; break; }
                    if ("failed".equals(evento.getStatus())) { aviso = "FALHA"; erro = "Meta não entregou o aviso. Confira o template e a conexão da linha remetente."; }
                }
            }
            return new Resumo(a.getId(), a.getNomeCliente(), a.getNumeroCliente(), a.getEstado().name(),
                    a.getResponsavel(), a.getSolicitadoEm(), service.janelaAberta(a), aviso, erro);
        }).toList();
    }
    @GetMapping("/{id}/mensagens") @Transactional(readOnly=true)
    public List<Mensagem> mensagens(@PathVariable Long barbeiroId, @PathVariable Long id) {
        atendimentos.findByIdAndBarbeiroId(id, barbeiroId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var lista = new ArrayList<>(mensagens.findTop100ByAtendimentoIdOrderByIdDesc(id));
        Collections.reverse(lista);
        return lista.stream().map(m -> new Mensagem(m.getId(), m.getAutor(), m.getTexto(), m.getCriadaEm())).toList();
    }
    @PostMapping("/{id}/assumir") public void assumir(@PathVariable Long barbeiroId, @PathVariable Long id, Principal p) { service.assumir(barbeiroId, id, p.getName()); }
    @PostMapping("/{id}/responder") public void responder(@PathVariable Long barbeiroId, @PathVariable Long id, Principal p, @Valid @RequestBody Resposta r) { service.responder(barbeiroId, id, p.getName(), r.texto()); }
    @PostMapping({"/{id}/finalizar", "/{id}/retomar-robo"}) public void finalizar(@PathVariable Long barbeiroId, @PathVariable Long id, Principal p) { service.finalizar(barbeiroId, id, p.getName()); }

    @PostMapping("/{id}/reenviar-aviso") @Transactional
    public void reenviarAviso(@PathVariable Long barbeiroId, @PathVariable Long id) {
        var a = service.buscarComTrava(barbeiroId, id);
        if (a.getEstado() != AtendimentoHumano.Estado.AGUARDANDO) throw new ResponseStatusException(HttpStatus.CONFLICT, "O pedido não está aguardando atendimento.");
        boolean falhou = a.getErroAviso() != null;
        if (a.getAvisoMensagemId() != null) {
            var eventos = status.findByMensagemIdMetaAndPhoneNumberIdOrderByRecebidoEmAsc(a.getAvisoMensagemId(), a.getAvisoPhoneNumberId());
            boolean entregue = eventos.stream().anyMatch(e -> "delivered".equals(e.getStatus()) || "read".equals(e.getStatus()));
            falhou = !entregue && eventos.stream().anyMatch(e -> "failed".equals(e.getStatus()));
        }
        if (!falhou) throw new ResponseStatusException(HttpStatus.CONFLICT, "Somente um aviso com falha pode ser reenviado.");
        a.setErroAviso(null); a.setAvisoMensagemId(null); a.setAvisoPhoneNumberId(null);
    }

    @GetMapping("/notificacoes") @Transactional(readOnly=true)
    public List<Notificacao> notificacoes(@PathVariable Long barbeiroId) {
        return notificacoes.findTop100ByAgendamentoBarbeiroIdOrderByCriadaEmDesc(barbeiroId).stream()
                .map(n -> new Notificacao(n.getId(), n.getTipo().name(), n.getStatus().name(), n.getTentativas(), n.getUltimoErro())).toList();
    }
    @PostMapping("/notificacoes/{id}/reenviar") @Transactional
    public void reenviar(@PathVariable Long barbeiroId, @PathVariable Long id) {
        var n = notificacoes.buscarParaEnviar(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!n.getAgendamento().getBarbeiro().getId().equals(barbeiroId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        n.solicitarReenvio();
    }
    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Map<String,String>> conflito(ResponseStatusException e) {
        return org.springframework.http.ResponseEntity.status(e.getStatusCode()).body(Map.of("erro", Objects.toString(e.getReason(), "Atendimento não encontrado.")));
    }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> invalido(IllegalArgumentException e) { return Map.of("erro", e.getMessage()); }
}

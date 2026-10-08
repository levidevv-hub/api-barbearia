package com.guilhermelevi.barbearia.admin;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.domain.Servico;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import com.guilhermelevi.barbearia.repositories.IServicoRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Operações exclusivas da sessão ADMIN; nunca usa um telefone como credencial. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/barbeiros/{barbeiroId}/servicos")
public class ServicoAdminController {
    private final IBarbeiroRepository barbeiros;
    private final IServicoRepository servicos;
    private final EntityManager em;

    public record Form(@NotBlank @Size(max=100) String nome,
                       @NotNull @DecimalMin("0.00") @Digits(integer=10, fraction=2) BigDecimal preco,
                       @NotNull @Min(1) @Max(1439) Integer duracaoMinutos,
                       @NotNull Boolean ativo) {}
    public record StatusForm(@NotNull Boolean ativo) {}
    public record Resumo(Long id, String nome, BigDecimal preco, Integer duracaoMinutos, boolean ativo) {
        static Resumo from(Servico s) {
            return new Resumo(s.getId(), s.getNome(), s.getPreco(), s.getDuracaoMinutos(), s.isAtivo());
        }
    }
    private Barbeiro buscarBarbeiro(Long id, boolean travar) {
        return (travar ? barbeiros.buscarParaAgendar(id) : barbeiros.findById(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profissional não encontrado."));
    }
    private Servico buscarServico(Long barbeiroId, Long id) {
        return servicos.findByIdAndBarbeiroId(id, barbeiroId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço não encontrado neste estabelecimento."));
    }
    private void preencher(Servico s, Form form) {
        s.setNome(form.nome().strip());
        s.setPreco(form.preco().setScale(2));
        s.setDuracaoMinutos(form.duracaoMinutos());
        s.setAtivo(form.ativo());
    }
    @GetMapping
    @Transactional(readOnly=true)
    public List<Resumo> listar(@PathVariable Long barbeiroId) {
        buscarBarbeiro(barbeiroId, false);
        return servicos.findByBarbeiroId(barbeiroId).stream()
                .sorted(Comparator.comparing(Servico::getId)).map(Resumo::from).toList();
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Resumo cadastrar(@PathVariable Long barbeiroId, @Valid @RequestBody Form form) {
        var s = new Servico();
        s.setBarbeiro(buscarBarbeiro(barbeiroId, true));
        preencher(s, form);
        return Resumo.from(servicos.saveAndFlush(s));
    }
    @PostMapping("/{servicoId}")
    @Transactional
    public Resumo editar(@PathVariable Long barbeiroId, @PathVariable Long servicoId, @Valid @RequestBody Form form) {
        buscarBarbeiro(barbeiroId, true); // Mesma trava usada na confirmação do agendamento.
        var s = buscarServico(barbeiroId, servicoId);
        preencher(s, form);
        // Agendamento.duracaoMinutos guarda a duração já contratada; não reescrevemos reservas.
        return Resumo.from(servicos.saveAndFlush(s));
    }
    @PostMapping("/{servicoId}/status")
    @Transactional
    public Resumo status(@PathVariable Long barbeiroId, @PathVariable Long servicoId, @Valid @RequestBody StatusForm form) {
        buscarBarbeiro(barbeiroId, true);
        var s = buscarServico(barbeiroId, servicoId);
        s.setAtivo(form.ativo());
        return Resumo.from(servicos.saveAndFlush(s));
    }
    @PostMapping("/{servicoId}/remover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void remover(@PathVariable Long barbeiroId, @PathVariable Long servicoId) {
        buscarBarbeiro(barbeiroId, true);
        var s = buscarServico(barbeiroId, servicoId);
        long reservas = em.createQuery("select count(a) from Agendamento a where a.servico.id = :id", Long.class)
                .setParameter("id", servicoId).getSingleResult();
        long conversas = em.createQuery("select count(s) from SessaoConversa s where s.servicoSelecionado.id = :id or s.servicoEmEdicao.id = :id", Long.class)
                .setParameter("id", servicoId).getSingleResult();
        long itens = em.createQuery("select count(a) from Agendamento a join a.itens i where i.servicoId = :id", Long.class)
                .setParameter("id", servicoId).getSingleResult();
        long selecoes = em.createQuery("select count(s) from SessaoConversa s join s.itensSelecionados i where i.servicoId = :id", Long.class)
                .setParameter("id", servicoId).getSingleResult();
        if (reservas > 0 || conversas > 0 || itens > 0 || selecoes > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este serviço está vinculado a agendamentos ou conversas. Use Desativar para retirá-lo de novos agendamentos sem apagar o histórico.");
        servicos.delete(s);
        servicos.flush();
    }
    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Map<String,String>> erro(ResponseStatusException e) {
        return org.springframework.http.ResponseEntity.status(e.getStatusCode()).body(Map.of("erro", e.getReason()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> validacao(MethodArgumentNotValidException e) {
        return Map.of("erro", e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(java.util.stream.Collectors.joining("; ")));
    }
}

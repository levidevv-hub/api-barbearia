package com.guilhermelevi.barbearia.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/barbeiros")
public class BarbeiroRemocaoController {
    private final BarbeiroRemocaoService remocao;
    public record Confirmacao(@NotBlank String nomeConfirmacao) {}

    @PostMapping("/{id}/remover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long id, @Valid @RequestBody Confirmacao form) {
        remocao.remover(id, form.nomeConfirmacao());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> erro(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("erro", e.getReason() == null ? "Não foi possível remover." : e.getReason()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String,String> vinculos(DataIntegrityViolationException e) {
        return Map.of("erro", "O profissional possui outros vínculos no sistema. A exclusão foi desfeita e os dados foram preservados.");
    }
}

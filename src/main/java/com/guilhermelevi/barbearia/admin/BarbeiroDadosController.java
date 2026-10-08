package com.guilhermelevi.barbearia.admin;

import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/barbeiros/{id}/dados")
public class BarbeiroDadosController {
    private final IBarbeiroRepository barbeiros;
    public record Dados(@NotBlank @Size(max=120) String nome,
                        @NotBlank @Pattern(regexp="[1-9][0-9]{7,14}") String numeroWhatsAppAdministrador,
                        @NotBlank @Pattern(regexp="[1-9][0-9]{7,14}") String numeroWhatsAppNotificacao,
                        @Size(max=80) String segmento) {
        public Dados(String nome, String administrador, String notificacao) {
            this(nome, administrador, notificacao, null);
        }
    }

    @GetMapping
    @Transactional(readOnly=true)
    public AdminController.BarbeiroResumo consultar(@PathVariable Long id) {
        return AdminController.BarbeiroResumo.from(barbeiros.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profissional não encontrado.")));
    }
    @PostMapping
    @Transactional
    public AdminController.BarbeiroResumo salvar(@PathVariable Long id, @Valid @RequestBody Dados dados) {
        var b = barbeiros.buscarParaAgendar(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profissional não encontrado."));
        b.setNome(dados.nome().strip());
        // Clientes antigos do painel podem omitir o campo.
        if (dados.segmento() != null) b.setSegmento(dados.segmento().strip());
        b.setNumeroWhatsAppAdministrador(dados.numeroWhatsAppAdministrador());
        b.setNumeroWhatsAppNotificacao(dados.numeroWhatsAppNotificacao());
        return AdminController.BarbeiroResumo.from(barbeiros.saveAndFlush(b));
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

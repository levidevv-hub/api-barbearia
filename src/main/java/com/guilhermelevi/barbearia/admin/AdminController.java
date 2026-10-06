package com.guilhermelevi.barbearia.admin;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import com.guilhermelevi.barbearia.service.ConexaoWhatsAppPendenteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.security.Principal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {
    private final IBarbeiroRepository barbeiros;
    private final ConexaoWhatsAppPendenteService conexoes;

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }
    @GetMapping("/session")
    public Map<String, String> session(Principal principal) { return Map.of("username", principal.getName()); }

    // DTO explicito: nunca serialize a entidade que contem credenciais da Meta.
    public record BarbeiroResumo(Long id, String nome, String numeroWhatsAppAdministrador,
            String numeroWhatsAppNotificacao, LocalTime inicioExpediente,
            LocalTime fimExpediente, String endereco, boolean vinculoRegistrado) {
        static BarbeiroResumo from(Barbeiro b) {
            return new BarbeiroResumo(b.getId(), b.getNome(), b.getNumeroWhatsAppAdministrador(),
                    b.getNumeroWhatsAppNotificacao(), b.getInicioExpediente(), b.getFimExpediente(),
                    b.getEndereco(), b.getWhatsappPhoneNumberId() != null && !b.getWhatsappPhoneNumberId().isBlank());
        }
    }
    @GetMapping("/barbeiros")
    public List<BarbeiroResumo> listar() {
        return barbeiros.findAll(Sort.by(Sort.Direction.DESC, "id")).stream().map(BarbeiroResumo::from).toList();
    }
    @PostMapping("/barbeiros") @ResponseStatus(HttpStatus.CREATED)
    public BarbeiroResumo cadastrar(@Valid @RequestBody BarbeiroForm form) {
        if (!form.getFimExpediente().isAfter(form.getInicioExpediente()))
            throw new IllegalArgumentException("O fim deve ser depois do início.");
        Barbeiro b = new Barbeiro();
        b.setNome(form.getNome().strip());
        b.setNumeroWhatsAppAdministrador(form.getNumeroWhatsAppAdministrador());
        b.setNumeroWhatsAppNotificacao(form.getNumeroWhatsAppNotificacao());
        b.setInicioExpediente(form.getInicioExpediente());
        b.setFimExpediente(form.getFimExpediente());
        b.setEndereco(form.getEndereco() == null ? null : form.getEndereco().strip());
        return BarbeiroResumo.from(barbeiros.save(b));
    }
    @PostMapping("/barbeiros/{id}/link")
    public Map<String, String> gerarLink(@PathVariable Long id) {
        return Map.of("link", "https://zaluratech.com.br/conectar-whatsapp?token=" + conexoes.gerarToken(id));
    }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalido(IllegalArgumentException e) { return Map.of("erro", e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> validacao(MethodArgumentNotValidException e) {
        return Map.of("erro", e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(java.util.stream.Collectors.joining("; ")));
    }
}

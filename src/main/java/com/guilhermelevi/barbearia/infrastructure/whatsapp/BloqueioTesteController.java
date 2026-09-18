package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import com.guilhermelevi.barbearia.service.BloqueioDataService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/testes/bloqueios")
@ConditionalOnProperty(
        name = "app.bloqueio-teste.habilitado",
        havingValue = "true"
)
public class BloqueioTesteController {

    private final BloqueioDataService service;
    private final String chave;

    public BloqueioTesteController(
            BloqueioDataService service,
            @Value("${app.bloqueio-teste.chave}") String chave
    ) {
        if (chave == null || chave.isBlank()) {
            throw new IllegalArgumentException(
                    "Configure a chave de acesso ao teste."
            );
        }

        this.service = service;
        this.chave = chave;
    }

    @GetMapping("/previa")
    public List<BloqueioDataService.ReservaAfetada> consultar(
            @RequestHeader("X-Chave-Teste") String chaveInformada,
            @RequestParam("barbeiroId") Long barbeiroId,
            @RequestParam("data")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate data
    ) {
        validarChave(chaveInformada);
        return service.consultarAfetados(barbeiroId, data);
    }

    @PostMapping("/confirmar")
    public Map<String, Object> confirmar(
            @RequestHeader("X-Chave-Teste") String chaveInformada,
            @RequestBody ConfirmacaoBloqueio pedido
    ) {
        validarChave(chaveInformada);

        int cancelados = service.confirmarBloqueio(
                pedido.barbeiroId(),
                pedido.data(),
                pedido.motivo(),
                pedido.idsConfirmados()
        );

        return Map.of(
                "bloqueado", true,
                "agendamentosCancelados", cancelados
        );
    }

    private void validarChave(String chaveInformada) {
        if (!chave.equals(chaveInformada)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    public record ConfirmacaoBloqueio(
            Long barbeiroId,
            LocalDate data,
            String motivo,
            List<Long> idsConfirmados
    ) {
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> tratarDadosInvalidos(
            IllegalArgumentException erro
    ) {
        return Map.of("erro", erro.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> tratarConflito(
            IllegalStateException erro
    ) {
        return Map.of("erro", erro.getMessage());
    }
}
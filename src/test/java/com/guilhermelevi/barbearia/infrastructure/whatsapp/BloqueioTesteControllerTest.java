package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import com.guilhermelevi.barbearia.service.BloqueioDataService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BloqueioTesteControllerTest {

    @Test
    void exigeChaveConfigurada() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BloqueioTesteController(
                        mock(BloqueioDataService.class),
                        " "
                )
        );
    }

    @Test
    void chaveInvalidaRetornaNaoAutorizado() {
        BloqueioTesteController controller =
                new BloqueioTesteController(
                        mock(BloqueioDataService.class),
                        "segredo"
                );

        ResponseStatusException erro = assertThrows(
                ResponseStatusException.class,
                () -> controller.consultar(
                        "errada",
                        1L,
                        LocalDate.now().plusDays(1)
                )
        );

        assertEquals(HttpStatus.UNAUTHORIZED, erro.getStatusCode());
    }

    @Test
    void consultarDelegaParaService() {
        BloqueioDataService service = mock(BloqueioDataService.class);
        BloqueioTesteController controller =
                new BloqueioTesteController(service, "segredo");

        LocalDate data = LocalDate.now().plusDays(1);
        var afetado = new BloqueioDataService.ReservaAfetada(
                10L,
                "Cliente",
                "Corte",
                LocalDateTime.now().plusDays(1)
        );

        when(service.consultarAfetados(1L, data))
                .thenReturn(List.of(afetado));

        assertEquals(
                List.of(afetado),
                controller.consultar("segredo", 1L, data)
        );
    }

    @Test
    void confirmarRetornaQuantidadeCancelada() {
        BloqueioDataService service = mock(BloqueioDataService.class);
        BloqueioTesteController controller =
                new BloqueioTesteController(service, "segredo");

        LocalDate data = LocalDate.now().plusDays(2);
        var pedido = new BloqueioTesteController.ConfirmacaoBloqueio(
                1L,
                data,
                "Folga",
                List.of(10L, 11L)
        );

        when(service.confirmarBloqueio(
                1L,
                data,
                "Folga",
                List.of(10L, 11L)
        )).thenReturn(2);

        var resposta = controller.confirmar("segredo", pedido);

        assertEquals(true, resposta.get("bloqueado"));
        assertEquals(2, resposta.get("agendamentosCancelados"));
    }
}

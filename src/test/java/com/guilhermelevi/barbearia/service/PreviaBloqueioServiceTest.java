package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.domain.PreviaBloqueio;
import com.guilhermelevi.barbearia.domain.exception.OperacaoAdministrativaException;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import com.guilhermelevi.barbearia.repositories.IPreviaBloqueioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PreviaBloqueioServiceTest {

    private IPreviaBloqueioRepository previas;
    private IBarbeiroRepository barbeiros;
    private AutorizacaoBarbeiroService autorizacao;
    private BloqueioDataService bloqueios;
    private PreviaBloqueioService service;

    @BeforeEach
    void preparar() {
        previas = mock(IPreviaBloqueioRepository.class);
        barbeiros = mock(IBarbeiroRepository.class);
        autorizacao = mock(AutorizacaoBarbeiroService.class);
        bloqueios = mock(BloqueioDataService.class);

        service = new PreviaBloqueioService(
                previas,
                barbeiros,
                autorizacao,
                bloqueios
        );
    }

    @Test
    void criarExigeAutorizacao() {
        when(autorizacao.podeAdministrar(1L, "5588999999999"))
                .thenReturn(false);

        assertThrows(
                OperacaoAdministrativaException.class,
                () -> service.criar(
                        1L,
                        "5588999999999",
                        LocalDate.now().plusDays(1)
                )
        );

        verifyNoInteractions(previas, bloqueios);
    }

    @Test
    void criarPersisteIdsDaPrevia() {
        LocalDate data = LocalDate.now().plusDays(2);
        Barbeiro barbeiro = Barbeiro.builder().id(1L).build();
        var afetado = new BloqueioDataService.ReservaAfetada(
                10L,
                "Cliente",
                "Corte",
                LocalDateTime.now().plusDays(2)
        );

        when(autorizacao.podeAdministrar(1L, "5588999999999"))
                .thenReturn(true);
        when(barbeiros.findById(1L)).thenReturn(Optional.of(barbeiro));
        when(bloqueios.consultarAfetados(1L, data))
                .thenReturn(List.of(afetado));
        when(previas.save(any(PreviaBloqueio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var resultado = service.criar(
                1L,
                "5588999999999",
                data
        );

        assertEquals(data, resultado.data());
        assertEquals(List.of(afetado), resultado.afetados());

        verify(previas).save(argThat(p ->
                p.getBarbeiro() == barbeiro
                        && p.getIdsAgendamentos().equals(Set.of(10L))
        ));
    }

    @Test
    void confirmarConsomePreviaEDelegaBloqueio() {
        UUID id = UUID.randomUUID();
        LocalDate data = LocalDate.now().plusDays(2);
        Barbeiro barbeiro = Barbeiro.builder().id(1L).build();
        PreviaBloqueio previa = new PreviaBloqueio(
                barbeiro,
                "5588999999999",
                data,
                Set.of(10L, 11L)
        );

        when(barbeiros.buscarParaAgendar(1L))
                .thenReturn(Optional.of(barbeiro));
        when(autorizacao.podeAdministrar(1L, "5588999999999"))
                .thenReturn(true);
        when(previas.buscarParaConfirmar(
                id,
                1L,
                "5588999999999"
        )).thenReturn(Optional.of(previa));
        when(bloqueios.confirmarBloqueio(
                eq(1L),
                eq(data),
                anyString(),
                argThat(ids -> ids.containsAll(Set.of(10L, 11L)))
        )).thenReturn(2);

        int cancelados = service.confirmar(
                id,
                1L,
                "5588999999999"
        );

        assertEquals(2, cancelados);
        assertTrue(previa.isConsumida());
    }
}

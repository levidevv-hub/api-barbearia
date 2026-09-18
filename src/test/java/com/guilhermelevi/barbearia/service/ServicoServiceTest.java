package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.domain.Servico;
import com.guilhermelevi.barbearia.domain.exception.OperacaoAdministrativaException;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import com.guilhermelevi.barbearia.repositories.IServicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicoServiceTest {

    private IServicoRepository servicos;
    private IBarbeiroRepository barbeiros;
    private AutorizacaoBarbeiroService autorizacao;
    private ServicoService service;
    private Barbeiro barbeiro;

    @BeforeEach
    void preparar() {
        servicos = mock(IServicoRepository.class);
        barbeiros = mock(IBarbeiroRepository.class);
        autorizacao = mock(AutorizacaoBarbeiroService.class);
        service = new ServicoService(servicos, barbeiros, autorizacao);

        barbeiro = Barbeiro.builder().id(1L).nome("Zalura").build();

        when(autorizacao.podeAdministrar(1L, "5588999999999"))
                .thenReturn(true);
        when(barbeiros.findById(1L)).thenReturn(Optional.of(barbeiro));
        when(barbeiros.buscarParaAgendar(1L)).thenReturn(Optional.of(barbeiro));
    }

    @Test
    void cadastrarNormalizaDados() {
        when(servicos.save(any(Servico.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Servico salvo = service.cadastrar(
                1L,
                "5588999999999",
                "  Corte  ",
                new BigDecimal("30.00"),
                45
        );

        assertEquals("Corte", salvo.getNome());
        assertEquals(new BigDecimal("30.00"), salvo.getPreco());
        assertEquals(45, salvo.getDuracaoMinutos());
        assertSame(barbeiro, salvo.getBarbeiro());
        assertTrue(salvo.isAtivo());
    }

    @Test
    void cadastrarRejeitaUsuarioNaoAutorizado() {
        when(autorizacao.podeAdministrar(1L, "5588000000000"))
                .thenReturn(false);

        assertThrows(
                OperacaoAdministrativaException.class,
                () -> service.cadastrar(
                        1L,
                        "5588000000000",
                        "Corte",
                        BigDecimal.TEN,
                        30
                )
        );

        verifyNoInteractions(servicos);
    }

    @Test
    void cadastrarRejeitaPrecoComMaisDeDuasCasas() {
        assertThrows(
                OperacaoAdministrativaException.class,
                () -> service.cadastrar(
                        1L,
                        "5588999999999",
                        "Corte",
                        new BigDecimal("10.001"),
                        30
                )
        );
    }

    @Test
    void editarAlteraCamposDoServicoDaBarbearia() {
        Servico existente = Servico.builder()
                .id(7L)
                .nome("Antigo")
                .preco(BigDecimal.TEN)
                .duracaoMinutos(30)
                .barbeiro(barbeiro)
                .build();

        when(servicos.findByIdAndBarbeiroId(7L, 1L))
                .thenReturn(Optional.of(existente));
        when(servicos.save(existente)).thenReturn(existente);

        Servico resultado = service.editar(
                1L,
                "5588999999999",
                7L,
                "Novo",
                new BigDecimal("25.50"),
                60
        );

        assertEquals("Novo", resultado.getNome());
        assertEquals(new BigDecimal("25.50"), resultado.getPreco());
        assertEquals(60, resultado.getDuracaoMinutos());
    }

    @Test
    void alterarStatusPersisteMudanca() {
        Servico existente = Servico.builder()
                .id(7L)
                .ativo(true)
                .barbeiro(barbeiro)
                .build();

        when(servicos.findByIdAndBarbeiroId(7L, 1L))
                .thenReturn(Optional.of(existente));
        when(servicos.saveAndFlush(existente)).thenReturn(existente);

        Servico resultado = service.alterarStatus(
                1L,
                "5588999999999",
                7L,
                false
        );

        assertFalse(resultado.isAtivo());
        verify(servicos).saveAndFlush(existente);
    }
}

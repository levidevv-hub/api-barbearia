package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Cliente;
import com.guilhermelevi.barbearia.repositories.IClienteRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClienteServiceTest {

    private final IClienteRepository repository = mock(IClienteRepository.class);
    private final ClienteService service = new ClienteService(repository);

    @Test
    void retornaClienteExistenteSemCriarOutro() {
        Cliente existente = Cliente.builder()
                .id(1L)
                .nomeCompleto("Cliente")
                .numeroTelefone("5588999999999")
                .build();

        when(repository.findByNumeroTelefone("5588999999999"))
                .thenReturn(Optional.of(existente));

        Cliente resultado = service.buscarOuCriar(
                "Nome novo",
                "5588999999999"
        );

        assertSame(existente, resultado);
        verify(repository, never()).save(any());
    }

    @Test
    void criaClienteQuandoTelefoneAindaNaoExiste() {
        when(repository.findByNumeroTelefone("5588999999999"))
                .thenReturn(Optional.empty());
        when(repository.save(any(Cliente.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Cliente resultado = service.buscarOuCriar(
                "Guilherme",
                "5588999999999"
        );

        assertEquals("Guilherme", resultado.getNomeCompleto());
        assertEquals("5588999999999", resultado.getNumeroTelefone());
        verify(repository).save(any(Cliente.class));
    }
}

package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutorizacaoBarbeiroServiceTest {

    private final IBarbeiroRepository repository = mock(IBarbeiroRepository.class);
    private final AutorizacaoBarbeiroService service =
            new AutorizacaoBarbeiroService(repository);

    @Test
    void rejeitaDadosInvalidosSemConsultarBanco() {
        assertFalse(service.podeAdministrar(null, "5588999999999"));
        assertFalse(service.podeAdministrar(1L, null));
        assertFalse(service.podeAdministrar(1L, "abc"));
        assertFalse(service.podeAdministrar(1L, "012345678"));

        verifyNoInteractions(repository);
    }

    @Test
    void autorizaSomenteNumeroAdministradorExato() {
        when(repository.findById(1L)).thenReturn(Optional.of(
                Barbeiro.builder()
                        .id(1L)
                        .numeroWhatsAppAdministrador("5588999999999")
                        .build()
        ));

        assertTrue(service.podeAdministrar(1L, "5588999999999"));
        assertFalse(service.podeAdministrar(1L, "5588888888888"));
    }

    @Test
    void retornaFalsoQuandoBarbeiroNaoExiste() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertFalse(service.podeAdministrar(99L, "5588999999999"));
    }
}

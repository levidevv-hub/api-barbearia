package com.guilhermelevi.barbearia.domain;

import com.guilhermelevi.barbearia.domain.enums.EtapaConversaEnum;
import com.guilhermelevi.barbearia.domain.enums.StatusAgendamentoEnum;
import com.guilhermelevi.barbearia.domain.exception.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnumsExceptionsTest {

    @Test
    void enumsContemEstadosEsperados() {
        assertNotNull(EtapaConversaEnum.valueOf("MENU"));
        assertNotNull(StatusAgendamentoEnum.valueOf("CONFIRMADO"));
        assertNotNull(StatusAgendamentoEnum.valueOf("CANCELADO"));
    }

    @Test
    void exceptionsPreservamMensagem() {
        assertEquals(
                "cancelamento",
                new CancelamentoNaoPermitidoException("cancelamento").getMessage()
        );
        assertEquals(
                "horario",
                new HorarioIndisponivelException("horario").getMessage()
        );
        assertEquals(
                "admin",
                new OperacaoAdministrativaException("admin").getMessage()
        );
        assertEquals(
                "alterado",
                new ServicoAlteradoException("alterado").getMessage()
        );
        assertEquals(
                "indisponivel",
                new ServicoIndisponivelException("indisponivel").getMessage()
        );
    }
}

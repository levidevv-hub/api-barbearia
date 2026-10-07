package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CentralWhatsappServiceTest {
    private final IBarbeiroRepository repository = mock(IBarbeiroRepository.class);
    private final CentralWhatsappService service = new CentralWhatsappService(repository);
    private void configurar(String id, String numero) {
        ReflectionTestUtils.setField(service, "phoneNumberId", id);
        ReflectionTestUtils.setField(service, "numero", numero);
    }
    @Test void desabilitadaMantemLinhaAtualENaoConsultaBanco() {
        configurar("", "");
        service.validarConfiguracao();
        assertFalse(service.ehCentral("990000"));
        assertFalse(service.ehRemetenteCentral("5511977777777"));
        assertEquals("linha-barbeiro", service.linhaNotificacao("linha-barbeiro"));
        verifyNoInteractions(repository);
    }
    @Test void exigeIdENumeroJuntos() {
        configurar("990000", "");
        assertThrows(IllegalStateException.class, service::validarConfiguracao);
        configurar("", "5511977777777");
        assertThrows(IllegalStateException.class, service::validarConfiguracao);
        configurar("numero-invalido", "5511977777777");
        assertThrows(IllegalStateException.class, service::validarConfiguracao);
    }
    @Test void roteiaAvisosParaLinhaCentralCadastrada() {
        configurar("990000", "5511977777777");
        service.validarConfiguracao();
        when(repository.findByWhatsappPhoneNumberId("990000")).thenReturn(Optional.of(new Barbeiro()));
        assertTrue(service.ehCentral("990000"));
        assertFalse(service.ehCentral("outra"));
        assertEquals("990000", service.linhaNotificacao("linha-barbeiro"));
    }
    @Test void configuracaoNaoCadastradaNaoUsaCredencialDeOutroBarbeiro() {
        configurar("990000", "5511977777777");
        when(repository.findByWhatsappPhoneNumberId("990000")).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> service.linhaNotificacao("linha-barbeiro"));
    }
    @Test void identificaCentralNasDuasRepresentacoesBrasileirasSemConfundirOutroNumero() {
        configurar("990000", "5511977777777");
        assertTrue(service.ehRemetenteCentral("5511977777777"));
        assertTrue(service.ehRemetenteCentral("551177777777"));
        assertFalse(service.ehRemetenteCentral("5511988888888"));
        assertFalse(service.ehRemetenteCentral(null));
    }
}

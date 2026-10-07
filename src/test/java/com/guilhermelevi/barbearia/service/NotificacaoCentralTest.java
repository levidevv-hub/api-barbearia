package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.WhatsAppClient;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificacaoCentralTest {
    private final IBarbeiroRepository repo = mock(IBarbeiroRepository.class);
    private final CentralWhatsappService central = new CentralWhatsappService(repo);
    private final WhatsAppClient whatsapp = mock(WhatsAppClient.class);
    private final NotificacaoService notificacao = new NotificacaoService(whatsapp, central);
    private void preparar() {
        ReflectionTestUtils.setField(central, "phoneNumberId", "990000");
        ReflectionTestUtils.setField(central, "numero", "5511977777777");
        ReflectionTestUtils.setField(notificacao, "usarTemplate", true);
        when(repo.findByWhatsappPhoneNumberId("990000")).thenReturn(Optional.of(new Barbeiro()));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }
    @AfterEach void limpar() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    @Test void cincoAgendasEnviamAvisosPelaCentralParaDestinatariosCorretosApenasAposCommit() {
        preparar();
        for (int i = 1; i <= 5; i++) notificacao.novoAgendamento(agendamento(i));
        verifyNoInteractions(whatsapp);
        var callbacks = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(5, callbacks.size());
        callbacks.forEach(s -> s.afterCommit());
        for (int i = 1; i <= 5; i++) verify(whatsapp).enviarTemplate(eq("990000"), eq("551198000000" + i),
                eq("novo_agendamento_barbeiro"), eq(List.of("Cliente " + i, "Corte", "Barbearia " + i, "10/01/2030", "10:00")));
    }
    @Test void cancelamentoTambemSaiPelaCentralMesmoComRoboDoBarbeiroPausado() {
        preparar(); var a = agendamento(1); a.getBarbeiro().setRoboAtivo(false);
        notificacao.agendamentoCancelado(a);
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        verify(whatsapp).enviarTemplate(eq("990000"), eq("5511980000001"), eq("agendamento_cancelado_barbeiro"), anyList());
    }
    @Test void erroDeConfiguracaoDeCentralNaoImpedeConfirmarReservaNemUsaLinhaErrada() {
        preparar(); when(repo.findByWhatsappPhoneNumberId("990000")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> notificacao.novoAgendamento(agendamento(1)));
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
        verifyNoInteractions(whatsapp);
    }
    private Agendamento agendamento(int i) {
        var b = Barbeiro.builder().nome("Barbearia " + i).whatsappPhoneNumberId("linha-" + i)
                .numeroWhatsAppNotificacao("551198000000" + i).build();
        var c = Cliente.builder().nomeCompleto("Cliente " + i).numeroTelefone("5511933333333").build();
        var s = Servico.builder().nome("Corte").barbeiro(b).preco(BigDecimal.TEN).duracaoMinutos(30).build();
        return new Agendamento(c, b, s, LocalDateTime.of(2030, 1, 10, 10, 0));
    }
}

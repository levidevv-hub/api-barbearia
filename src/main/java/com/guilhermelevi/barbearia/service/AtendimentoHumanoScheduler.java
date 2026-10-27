package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.repositories.IAtendimentoHumanoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component @RequiredArgsConstructor @Slf4j
public class AtendimentoHumanoScheduler {
    private final IAtendimentoHumanoRepository atendimentos;
    private final AtendimentoHumanoService service;
    @Scheduled(fixedDelayString="${whatsapp.atendimento.expiracao-intervalo-ms:30000}", initialDelayString="${whatsapp.atendimento.expiracao-intervalo-ms:30000}")
    public void reativarPausas() {
        var agora = LocalDateTime.now(ZoneId.of("America/Fortaleza"));
        for (Long id : atendimentos.buscarPausasExpiradas(agora, PageRequest.of(0, 100))) {
            try { service.reativarSeExpirado(id); }
            catch (RuntimeException e) { log.error("Falha ao reativar atendimento {}", id, e); }
        }
    }

    @Scheduled(fixedDelayString="${whatsapp.notificacao.intervalo-ms:10000}", initialDelayString="${whatsapp.notificacao.intervalo-ms:10000}")
    public void avisar() {
        for (var a : atendimentos.buscarAvisosPendentes(PageRequest.of(0,20))) {
            try { service.avisar(a.barbeiroId(), a.id()); }
            catch (RuntimeException e) { log.error("Falha ao processar aviso de atendimento {}", a.id(), e); }
        }
    }
}

package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.StatusMensagemRecebido;
import com.guilhermelevi.barbearia.repositories.IStatusMensagemRecebidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatusMensagemScheduler {

    private final IStatusMensagemRecebidoRepository repository;
    private final ProcessamentoStatusService processamentoService;

    @Scheduled(
            fixedDelayString = "${whatsapp.status.intervalo-ms:5000}",
            initialDelayString = "${whatsapp.status.intervalo-ms:5000}"
    )
    public void processarRecebidos() {
        List<StatusMensagemRecebido> eventos =
                repository.buscarProntosParaProcessar(
                        PageRequest.of(0, 50)
                );

        for (StatusMensagemRecebido evento : eventos) {
            try {
                processamentoService.processar(evento.getId());
            } catch (RuntimeException e) {
                log.error(
                        "Não foi possível processar o evento de status {}.",
                        evento.getId(),
                        e
                );
            }
        }
    }
}
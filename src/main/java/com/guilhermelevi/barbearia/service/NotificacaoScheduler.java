package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.NotificacaoPendente;
import com.guilhermelevi.barbearia.repositories.INotificacaoPendenteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacaoScheduler {

    private final INotificacaoPendenteRepository repository;
    private final EnvioNotificacaoService envioService;

    @Scheduled(
            fixedDelayString = "${whatsapp.notificacao.intervalo-ms:10000}",
            initialDelayString = "${whatsapp.notificacao.intervalo-ms:10000}"
    )
    public void processarPendentes() {
        List<NotificacaoPendente> pendentes =
                repository.findByStatusOrderByCriadaEmAscIdAsc(
                        NotificacaoPendente.Status.PENDENTE,
                        PageRequest.of(0, 20)
                );

        for (NotificacaoPendente notificacao : pendentes) {
            try {
                envioService.enviarPendente(notificacao.getId());
            } catch (RuntimeException e) {
                log.error(
                        "Não foi possível processar a notificação {}.",
                        notificacao.getId(),
                        e
                );
            }
        }
    }
}
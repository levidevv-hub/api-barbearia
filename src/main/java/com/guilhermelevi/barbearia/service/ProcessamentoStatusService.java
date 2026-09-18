package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.NotificacaoPendente;
import com.guilhermelevi.barbearia.domain.StatusMensagemRecebido;
import com.guilhermelevi.barbearia.repositories.INotificacaoPendenteRepository;
import com.guilhermelevi.barbearia.repositories.IStatusMensagemRecebidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessamentoStatusService {

    private final IStatusMensagemRecebidoRepository statusRepository;
    private final INotificacaoPendenteRepository notificacaoRepository;

    @Transactional
    public void processar(Long statusId) {
        StatusMensagemRecebido evento = statusRepository
                .buscarParaProcessar(statusId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Status recebido não encontrado."
                ));

        if (evento.isProcessado()) {
            return;
        }

        NotificacaoPendente notificacao = notificacaoRepository
                .findByMensagemIdMeta(evento.getMensagemIdMeta())
                .orElse(null);

        if (notificacao == null) {
            // O envio pode ainda não ter terminado de salvar o ID.
            // Mantemos o evento para uma tentativa posterior.
            return;
        }

        if (!Objects.equals(
                evento.getPhoneNumberId(),
                notificacao.getPhoneNumberId()
        )) {
            log.warn(
                    "Evento {} pertence a uma conta diferente da notificação {}.",
                    evento.getId(),
                    notificacao.getId()
            );

            evento.marcarProcessado();
            return;
        }

        // Uma entrega confirmada não deve regredir para falha.
        if (notificacao.getStatus()
                != NotificacaoPendente.Status.ENTREGUE) {

            switch (evento.getStatus()) {
                case "delivered", "read" ->
                        notificacao.registrarEntrega();

                case "failed" ->
                        notificacao.registrarFalha(evento.getErro());

                default -> log.debug(
                        "Status sem atualização prevista: {}.",
                        evento.getStatus()
                );
            }
        }

        evento.marcarProcessado();

        log.info(
                "Evento {} processado. Notificação {} está em {}.",
                evento.getId(),
                notificacao.getId(),
                notificacao.getStatus()
        );
    }
}
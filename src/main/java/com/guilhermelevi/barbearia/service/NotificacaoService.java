package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Agendamento;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.WhatsAppClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacaoService {

    private static final DateTimeFormatter FORMATO_DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("HH:mm");

    private final WhatsAppClient whatsappClient;

    @Value("${whatsapp.notificacao.usar-template:false}")
    private boolean usarTemplate;

    public void novoAgendamento(Agendamento agendamento) {
        prepararNotificacao(
                agendamento,
                "NOVO AGENDAMENTO",
                "novo_agendamento_barbeiro"
        );
    }

    public void agendamentoCancelado(Agendamento agendamento) {
        prepararNotificacao(
                agendamento,
                "AGENDAMENTO CANCELADO",
                "agendamento_cancelado_barbeiro"
        );
    }

    private void prepararNotificacao(
            Agendamento agendamento,
            String titulo,
            String nomeTemplate
    ) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "A notificação deve ser preparada dentro da transação."
            );
        }

        // Copiamos os dados antes de encerrar a transação.
        Long agendamentoId = agendamento.getId();
        String phoneNumberId =
                agendamento.getBarbeiro().getWhatsappPhoneNumberId();
        String destinatario =
                agendamento.getBarbeiro().getNumeroWhatsAppNotificacao();

        if (destinatario == null || destinatario.isBlank()) {
            log.warn(
                    "Agendamento {} sem destinatário para notificação.",
                    agendamentoId
            );
            return;
        }

        List<String> dados = List.of(
                agendamento.getCliente().getNomeCompleto(),
                agendamento.getServico().getNome(),
                agendamento.getBarbeiro().getNome(),
                agendamento.getInicio().format(FORMATO_DATA),
                agendamento.getInicio().format(FORMATO_HORA)
        );

        String mensagem = """
                %s

                Cliente: %s
                Serviço: %s
                Barbeiro: %s
                Data: %s
                Horário: %s
                """.formatted(
                titulo,
                dados.get(0),
                dados.get(1),
                dados.get(2),
                dados.get(3),
                dados.get(4)
        );

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            if (usarTemplate) {
                                whatsappClient.enviarTemplate(
                                        phoneNumberId,
                                        destinatario,
                                        nomeTemplate,
                                        dados
                                );
                            } else {
                                whatsappClient.enviarTexto(
                                        phoneNumberId,
                                        destinatario,
                                        mensagem
                                );
                            }

                            log.info(
                                    "API aceitou notificação {} do agendamento {}.",
                                    nomeTemplate,
                                    agendamentoId
                            );
                        } catch (RuntimeException e) {
                            log.error(
                                    "Falha na notificação {} do agendamento {}. "
                                            + "A alteração no banco foi mantida.",
                                    nomeTemplate,
                                    agendamentoId,
                                    e
                            );
                        }
                    }
                }
        );
    }
}
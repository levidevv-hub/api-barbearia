package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Agendamento;
import com.guilhermelevi.barbearia.domain.NotificacaoPendente;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.WhatsAppClient;
import com.guilhermelevi.barbearia.repositories.INotificacaoPendenteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnvioNotificacaoService {

    private static final DateTimeFormatter FORMATO_DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("HH:mm");

    private final INotificacaoPendenteRepository repository;
    private final WhatsAppClient whatsappClient;

    @Value("${whatsapp.notificacao.cliente.usar-template:false}")
    private boolean usarTemplate;

    @Transactional
    public void enviarPendente(Long notificacaoId) {
        NotificacaoPendente notificacao = repository
                .buscarParaEnviar(notificacaoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Notificação não encontrada."
                ));

        // Falhas serão tratadas separadamente, sem reenvio automático.
        if (notificacao.getStatus()
                != NotificacaoPendente.Status.PENDENTE) {
            return;
        }

        notificacao.registrarTentativa();

        try {
            String mensagemId;

            if (usarTemplate) {
                Agendamento agendamento = notificacao.getAgendamento();

                List<String> dados = List.of(
                        agendamento.getCliente().getNomeCompleto(),
                        agendamento.getServico().getNome(),
                        agendamento.getBarbeiro().getNome(),
                        agendamento.getInicio().format(FORMATO_DATA),
                        agendamento.getInicio().format(FORMATO_HORA)
                );

                mensagemId = whatsappClient.enviarTemplate(
                        notificacao.getPhoneNumberId(),
                        notificacao.getDestinatario(),
                        "cancelamento_por_bloqueio_cliente",
                        dados
                );
            } else {
                mensagemId = whatsappClient.enviarTexto(
                        notificacao.getPhoneNumberId(),
                        notificacao.getDestinatario(),
                        notificacao.getMensagem()
                );
            }

            notificacao.registrarAceite(mensagemId);

            log.info(
                    "API aceitou a notificação {}. Mensagem: {}",
                    notificacao.getId(),
                    mensagemId
            );
        } catch (RuntimeException e) {
            notificacao.registrarFalha(e.getMessage());

            log.error(
                    "Falha ao enviar a notificação {}.",
                    notificacao.getId(),
                    e
            );
        }
    }
}
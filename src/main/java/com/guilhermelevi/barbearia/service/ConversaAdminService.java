package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Agendamento;
import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.domain.exception.OperacaoAdministrativaException;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.WhatsAppClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversaAdminService {

    private final AutorizacaoBarbeiroService autorizacaoBarbeiroService;
    private final BloqueioDataService bloqueioDataService;
    private final PreviaBloqueioService previaBloqueioService;
    private final AgendamentoService agendamentoService;
    private final ConversaServicoAdminService servicoAdminService;
    private final WhatsAppClient whatsapp;

    public boolean processarTexto(
            String texto,
            Barbeiro barbeiro,
            String telefone
    ) {
        if (servicoAdminService.processarRespostaCadastroServico(
                texto, barbeiro, telefone
        )) {
            return true;
        }

        if (servicoAdminService.processarRespostaEdicaoServico(
                texto, barbeiro, telefone
        )) {
            return true;
        }

        if (processarPreviaBloqueio(texto, barbeiro, telefone)
                || processarLiberacaoDia(texto, barbeiro, telefone)
                || processarConsultaAgenda(texto, barbeiro, telefone)) {
            return true;
        }

        if (!"minha agenda".equalsIgnoreCase(texto)) {
            return false;
        }

        if (!autorizacaoBarbeiroService.podeAdministrar(
                barbeiro.getId(),
                telefone
        )) {
            whatsapp.enviarTextoAposCommit(
                    barbeiro.getWhatsappPhoneNumberId(),
                    telefone,
                    "Esse número não tem permissão para administrar esta agenda."
            );
            return true;
        }

        whatsapp.enviarMenuAdministrador(
                barbeiro.getWhatsappPhoneNumberId(),
                telefone
        );
        return true;
    }

    public boolean processarInteracao(
            String id,
            Barbeiro barbeiro,
            String telefone
    ) {
        if (!id.startsWith("ADMIN_")) {
            return false;
        }

        String phoneNumberId = barbeiro.getWhatsappPhoneNumberId();

        if (!autorizacaoBarbeiroService.podeAdministrar(
                barbeiro.getId(),
                telefone
        )) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Esse número não tem permissão para administrar esta agenda."
            );
            return true;
        }

        if (id.startsWith("ADMIN_CONFIRMAR_BLOQUEIO_")) {
            confirmarBloqueio(id, barbeiro, telefone);
            return true;
        }

        if (id.startsWith("ADMIN_EDITAR_SERVICO_")) {
            servicoAdminService.selecionarServicoParaEdicao(
                    id, barbeiro, telefone
            );
            return true;
        }

        if (id.startsWith("ADMIN_ATIVAR_SERVICO_")) {
            servicoAdminService.processarAlteracaoStatusServico(
                    id, barbeiro, telefone, true
            );
            return true;
        }

        if (id.startsWith("ADMIN_DESATIVAR_SERVICO_")) {
            servicoAdminService.processarAlteracaoStatusServico(
                    id, barbeiro, telefone, false
            );
            return true;
        }

        switch (id) {
            case "ADMIN_BLOQUEAR_DIA" ->
                    whatsapp.enviarTextoAposCommit(
                            phoneNumberId,
                            telefone,
                            "Para consultar o bloqueio de uma data, envie:\n\n"
                                    + "Bloquear 10/09/2026\n\n"
                                    + "Use a data desejada no formato dia/mês/ano. "
                                    + "Você verá os agendamentos afetados "
                                    + "antes de confirmar."
                    );

            case "ADMIN_LIBERAR_DIA" ->
                    whatsapp.enviarTextoAposCommit(
                            phoneNumberId,
                            telefone,
                            """
                            Para remover o bloqueio de uma data, envie:

                            Liberar 10/09/2026

                            Use a data desejada. O comando remove o bloqueio
                            imediatamente e mantém o expediente e as pausas.
                            Reservas canceladas não serão restauradas.
                            """
                    );

            case "ADMIN_VER_AGENDAMENTOS" ->
                    whatsapp.enviarTextoAposCommit(
                            phoneNumberId,
                            telefone,
                            """
                            Para consultar os agendamentos de uma data, envie:

                            Agenda 10/09/2026

                            Use a data desejada no formato dia/mês/ano.
                            """
                    );

            case "ADMIN_CADASTRAR_SERVICO" ->
                    servicoAdminService.iniciarCadastroServico(
                            barbeiro, telefone
                    );

            case "ADMIN_EDITAR_SERVICO" ->
                    servicoAdminService.iniciarEdicaoServico(
                            barbeiro, telefone
                    );

            case "ADMIN_ATIVAR_SERVICO" ->
                    servicoAdminService.mostrarServicosParaAlterarStatus(
                            barbeiro, telefone, true
                    );

            case "ADMIN_DESATIVAR_SERVICO" ->
                    servicoAdminService.mostrarServicosParaAlterarStatus(
                            barbeiro, telefone, false
                    );

            default ->
                    whatsapp.enviarTextoAposCommit(
                            phoneNumberId,
                            telefone,
                            "Opção administrativa inválida. Envie Minha agenda."
                    );
        }

        return true;
    }

    private void confirmarBloqueio(
            String id,
            Barbeiro barbeiro,
            String telefone
    ) {
        UUID previaId;

        try {
            previaId = UUID.fromString(
                    id.substring("ADMIN_CONFIRMAR_BLOQUEIO_".length())
            );
        } catch (IllegalArgumentException e) {
            whatsapp.enviarTextoAposCommit(
                    barbeiro.getWhatsappPhoneNumberId(),
                    telefone,
                    "Confirmação inválida. Solicite uma nova prévia."
            );
            return;
        }

        try {
            int cancelados = previaBloqueioService.confirmar(
                    previaId,
                    barbeiro.getId(),
                    telefone
            );

            whatsapp.enviarTextoAposCommit(
                    barbeiro.getWhatsappPhoneNumberId(),
                    telefone,
                    """
                    Dia bloqueado com sucesso.

                    Agendamentos cancelados: %d.
                    Avisos aos clientes registrados para envio.
                    """.formatted(cancelados)
            );
        } catch (OperacaoAdministrativaException e) {
            whatsapp.enviarTextoAposCommit(
                    barbeiro.getWhatsappPhoneNumberId(),
                    telefone,
                    e.getMessage()
            );
        }
    }

    public boolean processarPreviaBloqueio(
            String texto,
            Barbeiro barbeiro,
            String telefone
    ) {
        String[] partes = texto.strip().split("\\s+", 2);

        if (!"bloquear".equalsIgnoreCase(partes[0])) {
            return false;
        }

        String phoneNumberId = barbeiro.getWhatsappPhoneNumberId();

        if (!autorizacaoBarbeiroService.podeAdministrar(
                barbeiro.getId(), telefone
        )) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Esse número não tem permissão para administrar esta agenda."
            );
            return true;
        }

        if (partes.length != 2) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Informe a data. Exemplo: Bloquear 10/09/2026"
            );
            return true;
        }

        DateTimeFormatter formatoData = DateTimeFormatter
                .ofPattern("dd/MM/uuuu")
                .withResolverStyle(ResolverStyle.STRICT);

        LocalDate data;

        try {
            data = LocalDate.parse(partes[1].strip(), formatoData);
        } catch (DateTimeParseException e) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Data inválida. Use dia/mês/ano, como 10/09/2026."
            );
            return true;
        }

        PreviaBloqueioService.ResultadoPrevia previa;

        try {
            previa = previaBloqueioService.criar(
                    barbeiro.getId(),
                    telefone,
                    data
            );
        } catch (OperacaoAdministrativaException e) {
            whatsapp.enviarTextoAposCommit(phoneNumberId, telefone, e.getMessage());
            return true;
        }

        List<BloqueioDataService.ReservaAfetada> afetados = previa.afetados();

        StringBuilder mensagem = new StringBuilder(
                "Prévia do bloqueio de " + data.format(formatoData)
                        + "\nAgendamentos futuros afetados: "
                        + afetados.size() + "\n\n"
        );

        DateTimeFormatter formatoHora =
                DateTimeFormatter.ofPattern("HH:mm");

        for (BloqueioDataService.ReservaAfetada reserva : afetados) {
            String item = """
                Reserva #%d
                Cliente: %.100s
                Serviço: %.100s
                Horário: %s

                """.formatted(
                    reserva.agendamentoId(),
                    reserva.cliente(),
                    reserva.servico(),
                    reserva.inicio().format(formatoHora)
            );

            // Divide a prévia em mensagens quando houver muitas reservas.
            if (mensagem.length() + item.length() > 3500) {
                whatsapp.enviarTextoAposCommit(
                        phoneNumberId, telefone, mensagem.toString()
                );
                mensagem = new StringBuilder("Continuação da prévia:\n\n");
            }

            mensagem.append(item);
        }

        if (afetados.isEmpty()) {
            mensagem.append(
                    "Não há agendamentos futuros afetados nessa data.\n\n"
            );
        }

        mensagem.append(
                "Esta é apenas uma consulta. "
                        + "Nenhum bloqueio ou cancelamento foi realizado."
        );

        whatsapp.enviarTextoAposCommit(phoneNumberId, telefone, mensagem.toString());

        whatsapp.enviarConfirmacaoBloqueio(
                phoneNumberId,
                telefone,
                previa.id(),
                previa.data(),
                afetados.size()
        );

        return true;
    }

    public boolean processarLiberacaoDia(
            String texto,
            Barbeiro barbeiro,
            String telefone
    ) {
        String[] partes = texto.strip().split("\\s+", 2);

        if (!"liberar".equalsIgnoreCase(partes[0])) {
            return false;
        }

        String phoneNumberId = barbeiro.getWhatsappPhoneNumberId();

        if (!autorizacaoBarbeiroService.podeAdministrar(
                barbeiro.getId(), telefone
        )) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Esse número não tem permissão para administrar esta agenda."
            );
            return true;
        }

        if (partes.length != 2) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Informe a data. Exemplo: Liberar 10/09/2026"
            );
            return true;
        }

        DateTimeFormatter formatoData = DateTimeFormatter
                .ofPattern("dd/MM/uuuu")
                .withResolverStyle(ResolverStyle.STRICT);

        LocalDate data;

        try {
            data = LocalDate.parse(partes[1].strip(), formatoData);
        } catch (DateTimeParseException e) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Data inválida. Use dia/mês/ano, como 10/09/2026."
            );
            return true;
        }

        boolean removido;

        try {
            removido = bloqueioDataService.liberarDia(
                    barbeiro.getId(),
                    telefone,
                    data
            );
        } catch (OperacaoAdministrativaException e) {
            whatsapp.enviarTextoAposCommit(phoneNumberId, telefone, e.getMessage());
            return true;
        }

        String resposta = removido
                ? """
              Bloqueio de %s removido.

              A disponibilidade volta a seguir o expediente semanal
              e as pausas. Reservas canceladas não foram restauradas.
              """.formatted(data.format(formatoData))
                : "Não existe bloqueio excepcional para "
                + data.format(formatoData) + ".";

        whatsapp.enviarTextoAposCommit(phoneNumberId, telefone, resposta);
        return true;
    }

    public boolean processarConsultaAgenda(
            String texto,
            Barbeiro barbeiro,
            String telefone
    ) {
        String[] partes = texto.strip().split("\\s+", 2);

        if (!"agenda".equalsIgnoreCase(partes[0])) {
            return false;
        }

        String phoneNumberId = barbeiro.getWhatsappPhoneNumberId();

        if (!autorizacaoBarbeiroService.podeAdministrar(
                barbeiro.getId(), telefone
        )) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Esse número não tem permissão para consultar esta agenda."
            );
            return true;
        }

        if (partes.length != 2) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Informe a data. Exemplo: Agenda 10/09/2026"
            );
            return true;
        }

        DateTimeFormatter formatoData = DateTimeFormatter
                .ofPattern("dd/MM/uuuu")
                .withResolverStyle(ResolverStyle.STRICT);

        LocalDate data;

        try {
            data = LocalDate.parse(partes[1].strip(), formatoData);
        } catch (DateTimeParseException e) {
            whatsapp.enviarTextoAposCommit(
                    phoneNumberId,
                    telefone,
                    "Data inválida. Use dia/mês/ano, como 10/09/2026."
            );
            return true;
        }

        List<Agendamento> agendamentos;

        try {
            agendamentos = agendamentoService.listarAgendaDoDia(
                    barbeiro.getId(),
                    telefone,
                    data
            );
        } catch (OperacaoAdministrativaException e) {
            whatsapp.enviarTextoAposCommit(phoneNumberId, telefone, e.getMessage());
            return true;
        }

        whatsapp.enviarAgendaDoDia(
                phoneNumberId,
                telefone,
                data,
                agendamentos
        );

        return true;
    }

}

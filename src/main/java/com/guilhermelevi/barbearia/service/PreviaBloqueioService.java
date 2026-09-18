package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.domain.PreviaBloqueio;
import com.guilhermelevi.barbearia.domain.exception.OperacaoAdministrativaException;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import com.guilhermelevi.barbearia.repositories.IPreviaBloqueioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PreviaBloqueioService {

    private final IPreviaBloqueioRepository repository;
    private final IBarbeiroRepository barbeiroRepository;
    private final AutorizacaoBarbeiroService autorizacaoService;
    private final BloqueioDataService bloqueioDataService;

    @Transactional(
            isolation = Isolation.READ_COMMITTED,
            noRollbackFor = OperacaoAdministrativaException.class
    )
    public ResultadoPrevia criar(
            Long barbeiroId,
            String numeroAdministrador,
            LocalDate data
    ) {
        validarAutorizacao(barbeiroId, numeroAdministrador);

        Barbeiro barbeiro = barbeiroRepository.findById(barbeiroId)
                .orElseThrow(() -> new OperacaoAdministrativaException(
                        "Barbeiro não encontrado."
                ));

        List<BloqueioDataService.ReservaAfetada> afetados =
                bloqueioDataService.consultarAfetados(barbeiroId, data);

        Set<Long> ids = afetados.stream()
                .map(BloqueioDataService.ReservaAfetada::agendamentoId)
                .collect(Collectors.toSet());

        PreviaBloqueio previa = repository.save(
                new PreviaBloqueio(
                        barbeiro,
                        numeroAdministrador,
                        data,
                        ids
                )
        );

        return new ResultadoPrevia(
                previa.getId(),
                data,
                List.copyOf(afetados)
        );
    }

    @Transactional(
            isolation = Isolation.READ_COMMITTED,
            noRollbackFor = OperacaoAdministrativaException.class
    )
    public int confirmar(
            UUID previaId,
            Long barbeiroId,
            String numeroAdministrador
    ) {
        // Mantém a autorização estável durante a operação.
        barbeiroRepository.buscarParaAgendar(barbeiroId)
                .orElseThrow(() -> new OperacaoAdministrativaException(
                        "Barbeiro não encontrado."
                ));

        validarAutorizacao(barbeiroId, numeroAdministrador);

        PreviaBloqueio previa = repository.buscarParaConfirmar(
                        previaId,
                        barbeiroId,
                        numeroAdministrador
                )
                .orElseThrow(() -> new OperacaoAdministrativaException(
                        "Prévia não encontrada para este administrador."
                ));

        // Verifica expiração e impede reutilizar a mesma confirmação.
        previa.validarParaConfirmar();

        int cancelados = bloqueioDataService.confirmarBloqueio(
                barbeiroId,
                previa.getData(),
                "Bloqueio confirmado pelo administrador via WhatsApp",
                new ArrayList<>(previa.getIdsAgendamentos())
        );

        previa.consumir();

        return cancelados;
    }

    private void validarAutorizacao(
            Long barbeiroId,
            String numeroAdministrador
    ) {
        if (!autorizacaoService.podeAdministrar(
                barbeiroId,
                numeroAdministrador
        )) {
            throw new OperacaoAdministrativaException(
                    "Esse número não tem permissão para administrar esta agenda."
            );
        }
    }

    public record ResultadoPrevia(
            UUID id,
            LocalDate data,
            List<BloqueioDataService.ReservaAfetada> afetados
    ) {
    }
}
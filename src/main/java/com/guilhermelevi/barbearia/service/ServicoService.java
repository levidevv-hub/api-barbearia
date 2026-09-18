package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.domain.Servico;
import com.guilhermelevi.barbearia.domain.exception.OperacaoAdministrativaException;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import com.guilhermelevi.barbearia.repositories.IServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class ServicoService {

    private final IServicoRepository servicoRepository;
    private final IBarbeiroRepository barbeiroRepository;
    private final AutorizacaoBarbeiroService autorizacaoService;

    @Transactional(
            noRollbackFor = OperacaoAdministrativaException.class
    )
    public Servico cadastrar(
            Long barbeiroId,
            String numeroRemetente,
            String nome,
            BigDecimal preco,
            Integer duracaoMinutos
    ) {
        if (!autorizacaoService.podeAdministrar(
                barbeiroId,
                numeroRemetente
        )) {
            throw new OperacaoAdministrativaException(
                    "Você não tem permissão para cadastrar serviços nesta barbearia."
            );
        }

        if (nome == null || nome.isBlank()) {
            throw new OperacaoAdministrativaException(
                    "Informe o nome do serviço."
            );
        }

        String nomeAjustado = nome.strip();

        if (nomeAjustado.length() > 100) {
            throw new OperacaoAdministrativaException(
                    "O nome do serviço deve ter no máximo 100 caracteres."
            );
        }

        if (preco == null || preco.signum() < 0) {
            throw new OperacaoAdministrativaException(
                    "Informe um preço igual ou maior que zero."
            );
        }

        BigDecimal precoAjustado;

        try {
            precoAjustado = preco.setScale(
                    2,
                    RoundingMode.UNNECESSARY
            );
        } catch (ArithmeticException exception) {
            throw new OperacaoAdministrativaException(
                    "O preço deve ter no máximo duas casas decimais."
            );
        }

        if (duracaoMinutos == null || duracaoMinutos <= 0) {
            throw new OperacaoAdministrativaException(
                    "A duração deve ser maior que zero, em minutos."
            );
        }

        Barbeiro barbeiro = barbeiroRepository.findById(barbeiroId)
                .orElseThrow(() ->
                        new OperacaoAdministrativaException(
                                "Barbeiro não encontrado."
                        )
                );

        Servico servico = Servico.builder()
                .nome(nomeAjustado)
                .preco(precoAjustado)
                .duracaoMinutos(duracaoMinutos)
                .barbeiro(barbeiro)
                .build();

        return servicoRepository.save(servico);
    }

    @Transactional(
            isolation = Isolation.READ_COMMITTED,
            noRollbackFor = OperacaoAdministrativaException.class
    )
    public Servico editar(
            Long barbeiroId,
            String numeroRemetente,
            Long servicoId,
            String nome,
            BigDecimal preco,
            Integer duracaoMinutos
    ) {
        if (!autorizacaoService.podeAdministrar(
                barbeiroId,
                numeroRemetente
        )) {
            throw new OperacaoAdministrativaException(
                    "Você não tem permissão para editar serviços nesta barbearia."
            );
        }

        if (servicoId == null) {
            throw new OperacaoAdministrativaException(
                    "Selecione o serviço que deseja editar."
            );
        }

        barbeiroRepository.buscarParaAgendar(barbeiroId)
                .orElseThrow(() ->
                        new OperacaoAdministrativaException(
                                "Barbeiro não encontrado."
                        )
                );

        Servico servico = servicoRepository
                .findByIdAndBarbeiroId(servicoId, barbeiroId)
                .orElseThrow(() ->
                        new OperacaoAdministrativaException(
                                "Serviço não encontrado nesta barbearia."
                        )
                );

        if (nome == null || nome.isBlank()) {
            throw new OperacaoAdministrativaException(
                    "Informe o nome do serviço."
            );
        }

        String nomeAjustado = nome.strip();

        if (nomeAjustado.length() > 100) {
            throw new OperacaoAdministrativaException(
                    "O nome do serviço deve ter no máximo 100 caracteres."
            );
        }

        if (preco == null || preco.signum() < 0) {
            throw new OperacaoAdministrativaException(
                    "Informe um preço igual ou maior que zero."
            );
        }

        BigDecimal precoAjustado;

        try {
            precoAjustado = preco.setScale(
                    2,
                    RoundingMode.UNNECESSARY
            );
        } catch (ArithmeticException exception) {
            throw new OperacaoAdministrativaException(
                    "O preço deve ter no máximo duas casas decimais."
            );
        }

        if (duracaoMinutos == null || duracaoMinutos <= 0) {
            throw new OperacaoAdministrativaException(
                    "A duração deve ser maior que zero, em minutos."
            );
        }

        servico.setNome(nomeAjustado);
        servico.setPreco(precoAjustado);
        servico.setDuracaoMinutos(duracaoMinutos);

        return servicoRepository.save(servico);
    }

    @Transactional(
            isolation = Isolation.READ_COMMITTED,
            noRollbackFor = OperacaoAdministrativaException.class
    )
    public Servico alterarStatus(
            Long barbeiroId,
            String numeroRemetente,
            Long servicoId,
            boolean ativo
    ) {
        if (!autorizacaoService.podeAdministrar(
                barbeiroId,
                numeroRemetente
        )) {
            throw new OperacaoAdministrativaException(
                    "Você não tem permissão para alterar serviços nesta barbearia."
            );
        }

        if (servicoId == null) {
            throw new OperacaoAdministrativaException(
                    "Selecione o serviço que deseja alterar."
            );
        }

        barbeiroRepository.buscarParaAgendar(barbeiroId)
                .orElseThrow(() ->
                        new OperacaoAdministrativaException(
                                "Barbeiro não encontrado."
                        )
                );

        Servico servico = servicoRepository
                .findByIdAndBarbeiroId(servicoId, barbeiroId)
                .orElseThrow(() ->
                        new OperacaoAdministrativaException(
                                "Serviço não encontrado nesta barbearia."
                        )
                );

        servico.setAtivo(ativo);

        return servicoRepository.saveAndFlush(servico);
    }
}
package com.guilhermelevi.barbearia.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/** Valores apresentados/contratados, independentes de futuras edições do catálogo. */
@Embeddable
@Getter @NoArgsConstructor @AllArgsConstructor
public class ItemServico {
    @Column(name = "servico_id", nullable = false)
    private Long servicoId;
    @Column(nullable = false, length = 100)
    private String nome;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal preco;
    @Column(name = "duracao_minutos", nullable = false)
    private Integer duracaoMinutos;

    public static ItemServico de(Servico servico) {
        return new ItemServico(servico.getId(), servico.getNome(), servico.getPreco(), servico.getDuracaoMinutos());
    }
    public boolean corresponde(Servico servico) {
        return java.util.Objects.equals(nome, servico.getNome())
                && preco != null && servico.getPreco() != null && preco.compareTo(servico.getPreco()) == 0
                && java.util.Objects.equals(duracaoMinutos, servico.getDuracaoMinutos());
    }
    public static BigDecimal precoTotal(List<ItemServico> itens) {
        return itens.stream().map(ItemServico::getPreco).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public static int duracaoTotal(List<ItemServico> itens) {
        return itens.stream().map(ItemServico::getDuracaoMinutos).reduce(0, Math::addExact);
    }
    public static String nomes(List<ItemServico> itens) {
        return itens.stream().map(ItemServico::getNome).collect(Collectors.joining(" + "));
    }
}

package com.guilhermelevi.barbearia.domain;

import com.guilhermelevi.barbearia.domain.enums.EtapaConversaEnum;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sessoes_conversa")
@Builder
public class SessaoConversa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String numeroCliente;
    @ManyToOne
    private Barbeiro barbeiro;
    @Enumerated(EnumType.STRING)
    private EtapaConversaEnum etapa;
    @ManyToOne
    private Servico servicoSelecionado;
    private LocalDate dataSelecionada;
    private LocalTime horarioSelecionado;
    private LocalDateTime ultimaInteracao;
    private UUID confirmacaoId;
    private String nomeServicoEmCadastro;
    private BigDecimal precoServicoEmCadastro;
    private Integer duracaoServicoEmCadastro;
    @ManyToOne
    @JoinColumn(name = "servico_em_edicao_id")
    private Servico servicoEmEdicao;
    private BigDecimal precoServicoNaConfirmacao;
    private Integer duracaoServicoNaConfirmacao;

    private String nomeServicoEmEdicao;
    private BigDecimal precoServicoEmEdicao;
    private Integer duracaoServicoEmEdicao;

    public void atualizarInteracao() {
        this.ultimaInteracao = LocalDateTime.now();
    }

    public void limpar() {
        this.etapa = EtapaConversaEnum.MENU;
        this.servicoSelecionado = null;
        this.dataSelecionada = null;
        this.horarioSelecionado = null;
        this.confirmacaoId = null;

        limparCadastroServico();
        limparEdicaoServico();
        limparValoresConfirmacao();
    }

    public void limparCadastroServico() {
        this.nomeServicoEmCadastro = null;
        this.precoServicoEmCadastro = null;
        this.duracaoServicoEmCadastro = null;
    }

    public void limparEdicaoServico() {
        this.servicoEmEdicao = null;
        this.nomeServicoEmEdicao = null;
        this.precoServicoEmEdicao = null;
        this.duracaoServicoEmEdicao = null;
    }

    public void limparValoresConfirmacao() {
        this.precoServicoNaConfirmacao = null;
        this.duracaoServicoNaConfirmacao = null;
    }

}
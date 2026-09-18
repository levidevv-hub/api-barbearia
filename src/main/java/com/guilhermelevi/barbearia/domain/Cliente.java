package com.guilhermelevi.barbearia.domain;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "clientes")
@Builder
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nomeCompleto;
    @Column(nullable = false, unique = true)
    private String numeroTelefone;

    public Cliente(String nome, String numeroTelefone) {
        this.nomeCompleto = nome;
        this.numeroTelefone = numeroTelefone;
    }

}
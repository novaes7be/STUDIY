package com.studiy.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Registro de uma chamada de IA (tokens e custo). Nunca guarda o conteúdo enviado ou gerado. */
@Entity
@Table(name = "ia_chamadas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IaChamada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoGeracao tipo;

    @Column(nullable = false)
    private String modelo;

    private int tokensEntrada;
    private int tokensSaida;
    private int tokensCacheEscrita;
    private int tokensCacheLeitura;
    private long custoMicroUsd;

    @Column(nullable = false)
    private boolean sucesso;

    private String motivoParada;

    @Column(nullable = false)
    private LocalDateTime criadoEm;
}

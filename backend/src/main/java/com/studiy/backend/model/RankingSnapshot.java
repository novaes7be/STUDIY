package com.studiy.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Linha da "foto" do ranking (somente leitura pela API; escrita pelo RankingService). */
@Entity
@Table(name = "ranking_snapshot")
@Getter
@NoArgsConstructor
public class RankingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoRanking tipo;

    private int posicao;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private String apelido;

    private int valor;

    @Column(nullable = false)
    private LocalDateTime geradoEm;
}

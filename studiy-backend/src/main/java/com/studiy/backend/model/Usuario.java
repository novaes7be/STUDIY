package com.studiy.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Plano plano = Plano.FREE;

    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    // ---- Recuperação de senha ----
    private String resetTokenHash;
    private LocalDateTime resetTokenExpira;

    // ---- Sessões e aceite de termos (LGPD) ----
    @Builder.Default
    private Integer tokenVersion = 0;
    private LocalDateTime termosAceitosEm;
    private String termosVersao;

    // Foto de perfil (data URL já validada e redimensionada), cifrada em repouso
    @jakarta.persistence.Convert(converter = com.studiy.backend.config.CriptoConverter.class)
    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String avatar;

    // ---- Gamificação ----
    @Builder.Default
    private Integer xp = 0;

    @Builder.Default
    private Integer nivel = 1;

    @Builder.Default
    private Integer streakAtual = 0;

    private LocalDate ultimaAtividade;
}
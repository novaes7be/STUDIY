package com.studiy.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Resultado de IA já gerado para um conteúdo (HMAC do Markdown) de um usuário. Cifrado em repouso. */
@Entity
@Table(name = "geracoes_cache")
@Getter
@NoArgsConstructor
public class GeracaoCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "hash_conteudo", nullable = false, length = 64)
    private String hashConteudo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoGeracao tipo;

    @Convert(converter = com.studiy.backend.config.CriptoConverter.class)
    @Column(columnDefinition = "TEXT", nullable = false)
    private String conteudo;

    @Column(nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public GeracaoCache(Long usuarioId, String hashConteudo, TipoGeracao tipo, String conteudo) {
        this.usuarioId = usuarioId;
        this.hashConteudo = hashConteudo;
        this.tipo = tipo;
        this.conteudo = conteudo;
    }
}

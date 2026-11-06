package com.studiy.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Refresh token rotativo. Só o hash fica no banco; cada uso gera um novo token na mesma família. */
@Entity
@Table(name = "sessoes_refresh")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessaoRefresh {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 36)
    private String familia;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    @Column(nullable = false)
    private LocalDateTime expiraEm;

    /** A sessão passou pelo segundo fator (TOTP); as renovações herdam. */
    @Column(nullable = false)
    private boolean mfa;

    /** Preenchido quando o token é trocado por um novo; usar de novo = roubo provável. */
    private LocalDateTime usadoEm;
}

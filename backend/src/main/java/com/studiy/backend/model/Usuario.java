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

    // Fim do período pago do plano Pro (null = nunca assinou)
    private LocalDateTime planoExpiraEm;

    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    /** Plano realmente válido agora: o Pro vale só até planoExpiraEm. Toda regra de acesso usa ESTE método. */
    public Plano planoEfetivo() {
        boolean ativo = plano == Plano.PREMIUM && planoExpiraEm != null && planoExpiraEm.isAfter(LocalDateTime.now());
        return ativo ? Plano.PREMIUM : Plano.FREE;
    }

    // ---- Recuperação de senha ----
    private String resetTokenHash;
    private LocalDateTime resetTokenExpira;

    // ---- Sessões e aceite de termos (LGPD) ----
    @Builder.Default
    private Integer tokenVersion = 0;
    private LocalDateTime termosAceitosEm;
    private String termosVersao;

    // Papel e segundo fator (TOTP). Promoção a ADMIN só direto no banco; segredo cifrado em repouso.
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private Papel papel = Papel.USUARIO;
    @Convert(converter = com.studiy.backend.config.CriptoConverter.class)
    @Column(columnDefinition = "TEXT")
    private String mfaSegredo;
    @Builder.Default
    @Column(nullable = false)
    private boolean mfaAtivo = false;
    /** Último passo TOTP aceito: o mesmo código não vale duas vezes. */
    private Long mfaUltimoPasso;

    // Bloqueio progressivo após falhas de login
    @Builder.Default
    @Column(nullable = false)
    private int falhasLogin = 0;
    private LocalDateTime bloqueadoAte;

    // Onboarding do primeiro acesso (contas antigas já nascem concluídas; o cadastro grava false)
    @Builder.Default
    @Column(nullable = false)
    private boolean onboardingConcluido = true;

    // Verificação de e-mail (contas antigas já nascem verificadas).
    @Builder.Default
    @Column(nullable = false)
    private boolean emailVerificado = true;
    private String verifTokenHash;
    private LocalDateTime verifTokenExpira;

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

    /** Apelido público do ranking; null = fora do ranking. */
    @jakarta.persistence.Column(length = 20)
    private String apelido;
}

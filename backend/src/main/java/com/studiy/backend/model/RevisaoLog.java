package com.studiy.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Registro de uma revisão de flashcard (quem e quando). Base do ranking semanal. */
@Entity
@Table(name = "revisoes_log")
@Getter
@NoArgsConstructor
public class RevisaoLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    public RevisaoLog(Long usuarioId, LocalDateTime criadoEm) {
        this.usuarioId = usuarioId;
        this.criadoEm = criadoEm;
    }
}

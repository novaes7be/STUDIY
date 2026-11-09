package com.studiy.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "flashcards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Flashcard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Convert(converter = com.studiy.backend.config.CriptoConverter.class)
    @Column(columnDefinition = "TEXT", nullable = false)
    private String pergunta;

    @jakarta.persistence.Convert(converter = com.studiy.backend.config.CriptoConverter.class)
    @Column(columnDefinition = "TEXT", nullable = false)
    private String resposta;

    @Builder.Default
    private Boolean revisado = false;

    // ---- Repetição espaçada (algoritmo SM-2) ----
    @Builder.Default
    private Integer intervaloDias = 0;

    @Builder.Default
    private Double fatorFacilidade = 2.5;

    @Builder.Default
    private Integer repeticoes = 0;

    @Builder.Default
    private LocalDate proximaRevisao = LocalDate.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "materia_id", nullable = false)
    @JsonIgnore
    private Materia materia;

    /** PDF de origem (null nos cartões antigos): evita duplicar ao reaproveitar uma geração. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pdf_id")
    @JsonIgnore
    private Pdf pdf;

    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();
}

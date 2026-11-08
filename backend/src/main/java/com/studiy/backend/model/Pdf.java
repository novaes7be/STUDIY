package com.studiy.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "pdfs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pdf {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nomeArquivo;

    @jakarta.persistence.Convert(converter = com.studiy.backend.config.CriptoConverter.class)
    @Column(columnDefinition = "TEXT")
    private String textoExtraido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "materia_id", nullable = false)
    @JsonIgnore
    private Materia materia;

    /** MARKDOWN (novo) ou TEXTO (extraído antes da conversão; o conversor da inicialização atualiza). */
    @Builder.Default
    @Column(nullable = false, length = 10)
    private String formato = "MARKDOWN";

    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();
}

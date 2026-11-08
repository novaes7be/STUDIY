package com.studiy.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Figura de um PDF, referenciada no Markdown como imagem:numero. dados = base64, cifrado em repouso. */
@Entity
@Table(name = "pdf_imagens")
@Getter
@NoArgsConstructor
public class PdfImagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pdf_id", nullable = false)
    private Long pdfId;

    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false, length = 30)
    private String mime;

    @Column(nullable = false)
    private Integer largura;

    @Column(nullable = false)
    private Integer altura;

    @Convert(converter = com.studiy.backend.config.CriptoConverter.class)
    @Column(columnDefinition = "TEXT", nullable = false)
    private String dados;

    public PdfImagem(Long pdfId, Integer numero, String mime, Integer largura, Integer altura, String dados) {
        this.pdfId = pdfId;
        this.numero = numero;
        this.mime = mime;
        this.largura = largura;
        this.altura = altura;
        this.dados = dados;
    }
}

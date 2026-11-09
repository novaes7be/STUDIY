package com.studiy.backend.repository;

import com.studiy.backend.model.PdfImagem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PdfImagemRepository extends JpaRepository<PdfImagem, Long> {
    List<PdfImagem> findByPdfIdOrderByNumero(Long pdfId);
}

package com.studiy.backend.repository;

import com.studiy.backend.model.Pdf;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PdfRepository extends JpaRepository<Pdf, Long> {
    List<Pdf> findByMateriaId(Long materiaId);
    List<Pdf> findByMateriaIdOrderByCriadoEmDesc(Long materiaId);
    java.util.Optional<Pdf> findByIdAndMateriaId(Long id, Long materiaId);
    List<Pdf> findTop50ByFormatoNot(String formato);
}

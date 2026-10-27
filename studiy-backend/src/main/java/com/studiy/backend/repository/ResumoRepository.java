package com.studiy.backend.repository;

import com.studiy.backend.model.Resumo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResumoRepository extends JpaRepository<Resumo, Long> {
    List<Resumo> findByMateriaIdOrderByCriadoEmDesc(Long materiaId);
}

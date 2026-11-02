package com.studiy.backend.repository;

import com.studiy.backend.model.Simulado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SimuladoRepository extends JpaRepository<Simulado, Long> {
    List<Simulado> findByMateriaIdOrderByDataRealizacaoAsc(Long materiaId);
}

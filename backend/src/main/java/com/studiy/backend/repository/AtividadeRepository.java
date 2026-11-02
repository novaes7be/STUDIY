package com.studiy.backend.repository;

import com.studiy.backend.model.Atividade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AtividadeRepository extends JpaRepository<Atividade, Long> {
    List<Atividade> findByMateriaIdOrderByDataAtividadeAsc(Long materiaId);
    Optional<Atividade> findByIdAndMateriaId(Long id, Long materiaId);
}

package com.studiy.backend.repository;

import com.studiy.backend.model.Materia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MateriaRepository extends JpaRepository<Materia, Long> {
    List<Materia> findByUsuarioId(Long usuarioId);
    Optional<Materia> findByIdAndUsuarioId(Long id, Long usuarioId);
}

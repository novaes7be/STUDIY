package com.studiy.backend.repository;

import com.studiy.backend.model.Flashcard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {
    List<Flashcard> findByMateriaId(Long materiaId);
    Optional<Flashcard> findByIdAndMateriaId(Long id, Long materiaId);

    long countByMateriaIdAndProximaRevisaoLessThanEqual(Long materiaId, LocalDate data);
    List<Flashcard> findByMateriaIdAndProximaRevisaoLessThanEqualOrderByProximaRevisaoAsc(Long materiaId, LocalDate data);
}

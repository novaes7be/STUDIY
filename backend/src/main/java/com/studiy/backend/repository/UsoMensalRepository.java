package com.studiy.backend.repository;

import com.studiy.backend.model.UsoMensal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsoMensalRepository extends JpaRepository<UsoMensal, Long> {
    Optional<UsoMensal> findByUsuarioIdAndMesReferencia(Long usuarioId, String mesReferencia);
}

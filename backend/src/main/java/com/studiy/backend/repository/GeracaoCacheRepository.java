package com.studiy.backend.repository;

import com.studiy.backend.model.GeracaoCache;
import com.studiy.backend.model.TipoGeracao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GeracaoCacheRepository extends JpaRepository<GeracaoCache, Long> {
    Optional<GeracaoCache> findByUsuarioIdAndHashConteudoAndTipo(Long usuarioId, String hashConteudo, TipoGeracao tipo);
}

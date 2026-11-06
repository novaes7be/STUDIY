package com.studiy.backend.repository;

import com.studiy.backend.model.SessaoRefresh;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SessaoRefreshRepository extends JpaRepository<SessaoRefresh, Long> {

    /** Trava a linha: duas renovações simultâneas com o mesmo token não geram duas sessões. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SessaoRefresh s join fetch s.usuario where s.tokenHash = :hash")
    Optional<SessaoRefresh> buscarComTrava(@Param("hash") String hash);

    @Modifying
    @Query("delete from SessaoRefresh s where s.familia = :familia")
    void revogarFamilia(@Param("familia") String familia);

    @Modifying
    @Query("delete from SessaoRefresh s where s.usuario.id = :usuarioId")
    void revogarDoUsuario(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("delete from SessaoRefresh s where s.expiraEm < :agora")
    int apagarExpiradas(@Param("agora") LocalDateTime agora);
}

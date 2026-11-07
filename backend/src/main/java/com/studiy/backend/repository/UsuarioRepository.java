package com.studiy.backend.repository;

import com.studiy.backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByApelidoIgnoreCaseAndIdNot(String apelido, Long id);
    Optional<Usuario> findByResetTokenHash(String resetTokenHash);
    Optional<Usuario> findByVerifTokenHash(String verifTokenHash);

    /** clearAutomatically: a leitura seguinte vem do banco, não da entidade em cache (open-in-view). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Usuario u set u.falhasLogin = u.falhasLogin + 1 where u.id = :id")
    void incrementarFalhasLogin(@Param("id") Long id);

    @Modifying
    @Query("update Usuario u set u.resetTokenHash = null, u.resetTokenExpira = null where u.resetTokenExpira < :agora")
    int limparTokensResetExpirados(@Param("agora") java.time.LocalDateTime agora);

    @Modifying
    @Query("update Usuario u set u.verifTokenHash = null, u.verifTokenExpira = null where u.verifTokenExpira < :agora")
    int limparTokensVerificacaoExpirados(@Param("agora") java.time.LocalDateTime agora);
}

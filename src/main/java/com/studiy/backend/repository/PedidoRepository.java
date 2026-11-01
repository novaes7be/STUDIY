package com.studiy.backend.repository;

import com.studiy.backend.model.Pedido;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, String> {

    /** Isolamento: só devolve o pedido se ele for do usuário informado. */
    Optional<Pedido> findByIdAndUsuarioId(String id, Long usuarioId);

    /** Trava a linha para que dois webhooks simultâneos não ativem o mesmo pedido duas vezes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.id = :id")
    Optional<Pedido> buscarComTrava(@Param("id") String id);
}

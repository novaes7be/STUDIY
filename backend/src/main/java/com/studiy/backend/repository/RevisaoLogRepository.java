package com.studiy.backend.repository;

import com.studiy.backend.model.RevisaoLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface RevisaoLogRepository extends JpaRepository<RevisaoLog, Long> {

    @Modifying
    @Query("delete from RevisaoLog r where r.criadoEm < :limite")
    int apagarAnterioresA(@Param("limite") LocalDateTime limite);
}

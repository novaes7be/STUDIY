package com.studiy.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "uso_mensal", uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "mesReferencia"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsoMensal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    @JsonIgnore
    private Usuario usuario;

    /** Formato yyyy-MM */
    @Column(nullable = false)
    private String mesReferencia;

    @Builder.Default
    private Integer pdfsUsados = 0;
}

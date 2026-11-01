package com.studiy.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Um pedido de 30 dias de Studiy Pro. Valor e plano são definidos SEMPRE no servidor. */
@Entity
@Table(name = "pedidos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pedido {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetodoPagamento metodo;

    @Column(nullable = false)
    private Integer valorCentavos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPedido status;

    private String mpPreferenceId;

    @Column(unique = true)
    private String mpPaymentId;

    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    private LocalDateTime pagoEm;
}

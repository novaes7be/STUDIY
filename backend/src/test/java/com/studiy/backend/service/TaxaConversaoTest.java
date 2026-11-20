package com.studiy.backend.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TaxaConversaoTest {

    @Test
    void semPedidosNaoHaTaxa() {
        assertThat(AdminMetricasService.taxaConversao(Map.of())).isNull();
    }

    @Test
    void reembolsadoContaComoVendaEPendenteEntraNaBase() {
        var porStatus = Map.of("PAGO", 3L, "REEMBOLSADO", 1L, "PENDENTE", 4L, "REJEITADO", 2L);
        assertThat(AdminMetricasService.taxaConversao(porStatus)).isCloseTo(0.4, within(1e-9));
    }
}

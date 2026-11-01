package com.studiy.backend.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    @Test
    void bloqueiaDepoisDoLimiteEIsolaChaves() {
        RateLimitFilter filtro = new RateLimitFilter(3, 3, 3);
        assertTrue(filtro.permitir("auth:1.1.1.1", 3));
        assertTrue(filtro.permitir("auth:1.1.1.1", 3));
        assertTrue(filtro.permitir("auth:1.1.1.1", 3));
        assertFalse(filtro.permitir("auth:1.1.1.1", 3));
        assertTrue(filtro.permitir("auth:2.2.2.2", 3));
    }
}

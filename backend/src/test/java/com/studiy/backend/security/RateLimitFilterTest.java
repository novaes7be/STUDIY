package com.studiy.backend.security;

import com.studiy.backend.security.ratelimit.LimitadorLocal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    @Test
    void bloqueiaDepoisDoLimiteEIsolaChaves() {
        LimitadorLocal l = new LimitadorLocal();
        assertTrue(l.consumir("auth:1.1.1.1", 3));
        assertTrue(l.consumir("auth:1.1.1.1", 3));
        assertTrue(l.consumir("auth:1.1.1.1", 3));
        assertFalse(l.consumir("auth:1.1.1.1", 3));
        assertTrue(l.consumir("auth:2.2.2.2", 3));
    }
}

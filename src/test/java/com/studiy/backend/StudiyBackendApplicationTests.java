package com.studiy.backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Teste de contexto completo: precisa de PostgreSQL rodando (perfil dev).
 * Remova o @Disabled quando o banco de testes estiver configurado.
 * TODO: testes de isolamento com 2 usuários (Alice não pode ler matéria/PDF/resumo do Bruno) via MockMvc + Testcontainers.
 */
@Disabled("Requer PostgreSQL local")
@SpringBootTest
class StudiyBackendApplicationTests {
    @Test
    void contextLoads() {}
}

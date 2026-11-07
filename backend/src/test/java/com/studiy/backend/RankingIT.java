package com.studiy.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.studiy.backend.model.RevisaoLog;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.RevisaoLogRepository;
import com.studiy.backend.service.RankingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ranking global: só quem tem apelido e e-mail verificado; nenhuma informação pessoal na resposta. */
class RankingIT extends IntegracaoBase {

    @Autowired RankingService ranking;
    @Autowired RevisaoLogRepository revisoesLog;

    private static String apelidoNovo(String prefixo) {
        return prefixo + (System.nanoTime() % 100_000_000L);
    }

    /** Cria uma conta logada com apelido (ou sem, se null), revisões recentes e estado de gamificação. */
    private Sessao participante(String apelido, int revisoes, int xp, int streak, LocalDate ultima) throws Exception {
        Sessao s = novaSessao();
        Usuario u = usuarios.findByEmail(s.email()).orElseThrow();
        u.setApelido(apelido);
        u.setXp(xp);
        u.setStreakAtual(streak);
        u.setUltimaAtividade(ultima);
        usuarios.save(u);
        for (int i = 0; i < revisoes; i++) revisoesLog.save(new RevisaoLog(u.getId(), LocalDateTime.now().minusHours(i + 1)));
        return s;
    }

    private MvcResult pedir(Sessao s, String tipo) throws Exception {
        return mvc.perform(get("/api/ranking").param("tipo", tipo).cookie(s.acesso())).andExpect(status().isOk()).andReturn();
    }

    private static JsonNode linha(JsonNode resp, String apelido) {
        return StreamSupport.stream(resp.get("itens").spliterator(), false)
                .filter(i -> i.get("apelido").asText().equals(apelido)).findFirst().orElse(null);
    }

    @Test
    void entramSoContasComApelidoEEmailVerificado() throws Exception {
        String a = apelidoNovo("A"), c = apelidoNovo("C");
        Sessao sa = participante(a, 5, 50, 2, LocalDate.now());
        participante(null, 9, 90, 3, LocalDate.now());                 // sem apelido: fora
        Sessao sc = participante(c, 7, 70, 4, LocalDate.now());
        Usuario naoVerificado = usuarios.findByEmail(sc.email()).orElseThrow();
        naoVerificado.setEmailVerificado(false);                        // e-mail não confirmado: fora
        usuarios.save(naoVerificado);

        ranking.atualizar();

        JsonNode r = ler(pedir(sa, "SEMANA"));
        assertThat(r.get("participando").asBoolean()).isTrue();
        assertThat(r.get("geradoEm").isNull()).isFalse();
        assertThat(linha(r, a).get("valor").asInt()).isEqualTo(5);
        assertThat(linha(r, a).get("voce").asBoolean()).isTrue();
        assertThat(linha(r, c)).isNull();
        assertThat(r.get("voce").get("apelido").asText()).isEqualTo(a);
    }

    @Test
    void respostaNaoExpoeDadosPessoais() throws Exception {
        String a = apelidoNovo("P");
        Sessao s = participante(a, 3, 30, 1, LocalDate.now());
        ranking.atualizar();

        for (String tipo : new String[] {"SEMANA", "SEQUENCIA", "XP"}) {
            String corpo = pedir(s, tipo).getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(corpo).doesNotContain(s.email()).doesNotContain("\"email\"")
                    .doesNotContain("\"id\"").doesNotContain("usuario").doesNotContain("\"nome\"");
        }
    }

    @Test
    void sequenciaQuebradaNaoEntraEEmpateDividePosicao() throws Exception {
        String quebrada = apelidoNovo("Q"), viva = apelidoNovo("V"), f = apelidoNovo("F"), g = apelidoNovo("G");
        participante(quebrada, 0, 10, 5, LocalDate.now().minusDays(3));   // parou há 3 dias
        Sessao sv = participante(viva, 0, 10, 3, LocalDate.now().minusDays(1));
        participante(f, 4, 10, 1, LocalDate.now());
        participante(g, 4, 10, 1, LocalDate.now());
        ranking.atualizar();

        JsonNode seq = ler(pedir(sv, "SEQUENCIA"));
        assertThat(linha(seq, quebrada)).isNull();
        assertThat(linha(seq, viva).get("valor").asInt()).isEqualTo(3);

        JsonNode semana = ler(pedir(sv, "SEMANA"));
        assertThat(linha(semana, f).get("posicao").asInt()).isEqualTo(linha(semana, g).get("posicao").asInt());
        assertThat(semana.get("voce").isNull()).isTrue();                  // a conta "viva" não revisou na semana
    }

    @Test
    void exigeLoginERejeitaTipoInvalido() throws Exception {
        mvc.perform(get("/api/ranking")).andExpect(status().isUnauthorized());
        Sessao s = novaSessao();
        mvc.perform(get("/api/ranking").param("tipo", "QUALQUER").cookie(s.acesso())).andExpect(status().isBadRequest());
    }
}

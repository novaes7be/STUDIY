package com.studiy.backend;

import com.studiy.backend.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Bloqueio progressivo por conta, resposta idêntica para todos os casos de falha e tempo mínimo de resposta. */
class LoginBloqueioIT extends IntegracaoBase {

    private Sessao contaVerificada() throws Exception {
        return novaSessao();
    }

    @Test
    void contaBloqueiaDepoisDeCincoFalhasAteComSenhaCerta() throws Exception {
        String email = contaVerificada().email();

        for (int i = 0; i < 5; i++) {
            assertThat(tentarLogin(email, "senha-errada-" + i).getResponse().getStatus()).isEqualTo(401);
        }
        Usuario u = usuarios.findByEmail(email).orElseThrow();
        assertThat(u.getFalhasLogin()).isEqualTo(5);
        assertThat(u.getBloqueadoAte()).isAfter(LocalDateTime.now());

        MvcResult comSenhaCerta = tentarLogin(email, SENHA);
        MvcResult inexistente = tentarLogin(emailNovo(), SENHA);
        assertThat(comSenhaCerta.getResponse().getStatus()).isEqualTo(401);
        assertThat(comSenhaCerta.getResponse().getContentAsString())
                .isEqualTo(inexistente.getResponse().getContentAsString());
    }

    @Test
    void depoisDoBloqueioLoginCertoZeraOContador() throws Exception {
        String email = contaVerificada().email();
        for (int i = 0; i < 5; i++) tentarLogin(email, "errada");

        Usuario u = usuarios.findByEmail(email).orElseThrow();
        u.setBloqueadoAte(LocalDateTime.now().minusSeconds(1)); // simula o fim da espera
        usuarios.save(u);

        assertThat(tentarLogin(email, SENHA).getResponse().getStatus()).isEqualTo(200);
        Usuario depois = usuarios.findByEmail(email).orElseThrow();
        assertThat(depois.getFalhasLogin()).isZero();
        assertThat(depois.getBloqueadoAte()).isNull();
    }

    @Test
    void emailInexistenteESenhaErradaTemMesmaRespostaETempoMinimo() throws Exception {
        String email = contaVerificada().email();

        long t0 = System.nanoTime();
        MvcResult senhaErrada = tentarLogin(email, "errada");
        long ms1 = (System.nanoTime() - t0) / 1_000_000;

        t0 = System.nanoTime();
        MvcResult inexistente = tentarLogin(emailNovo(), "errada");
        long ms2 = (System.nanoTime() - t0) / 1_000_000;

        assertThat(senhaErrada.getResponse().getContentAsString())
                .isEqualTo(inexistente.getResponse().getContentAsString());
        assertThat(ms1).isGreaterThanOrEqualTo(400);
        assertThat(ms2).isGreaterThanOrEqualTo(400);
    }
}

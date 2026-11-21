package com.studiy.backend;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** A API responde no idioma do Accept-Language (o da página) e guarda esse idioma na conta. */
class IdiomaIT extends IntegracaoBase {

    private MvcResult registrar(String email, String senha, String idioma) throws Exception {
        var req = post("/api/auth/registrar").with(comCsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("nome", "Teste", "email", email, "senha", senha, "aceitoTermos", true)));
        if (idioma != null) req.header("Accept-Language", idioma);
        return mvc.perform(req).andReturn();
    }

    private MvcResult login(String email, String senha, String idioma) throws Exception {
        return mvc.perform(post("/api/auth/login").with(comCsrf()).contentType(MediaType.APPLICATION_JSON)
                .header("Accept-Language", idioma)
                .content(corpo(Map.of("email", email, "senha", senha)))).andReturn();
    }

    @Test
    void errosDeValidacaoSaemNoIdiomaDaPagina() throws Exception {
        JsonNode en = ler(registrar(emailNovo(), "curta", "en"));
        assertThat(en.get("erro").asText()).isEqualTo("Invalid data");
        assertThat(en.get("campos").get("senha").asText()).isEqualTo("The password must be 8 to 72 characters long");

        JsonNode es = ler(registrar(emailNovo(), "curta", "es-AR"));
        assertThat(es.get("campos").get("senha").asText()).isEqualTo("La contraseña debe tener entre 8 y 72 caracteres");

        JsonNode semCabecalho = ler(registrar(emailNovo(), "curta", null));
        assertThat(semCabecalho.get("erro").asText()).isEqualTo("Dados inválidos");
    }

    @Test
    void cadastroGuardaOIdiomaEMandaOEmailNele() throws Exception {
        String email = emailNovo();
        MvcResult r = registrar(email, SENHA, "en-US,en;q=0.9");

        assertThat(ler(r).get("mensagem").asText()).startsWith("Done!");
        assertThat(usuarios.findByEmail(email).orElseThrow().getIdioma()).isEqualTo("en");
        verify(emailService).enviarVerificacao(eq(email), anyString(), eq("en"));
    }

    @Test
    void loginAtualizaOIdiomaETraduzOErro() throws Exception {
        String email = emailNovo();
        registrar(email, SENHA, "en");

        assertThat(ler(login(email, "senha-errada-123", "es")).get("erro").asText())
                .startsWith("Correo o contraseña inválidos.");

        assertThat(login(email, SENHA, "es").getResponse().getStatus()).isEqualTo(200);
        assertThat(usuarios.findByEmail(email).orElseThrow().getIdioma()).isEqualTo("es");
    }
}

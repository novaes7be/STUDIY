package com.studiy.backend.i18n;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TradutorMensagensTest {

    private final TradutorMensagens tradutor = new TradutorMensagens(new ObjectMapper());

    @Test
    void traduzMensagemFixaEComValores() {
        assertThat(tradutor.traduzir("Matéria não encontrada", "en")).isEqualTo("Subject not found");
        assertThat(tradutor.traduzir("PDF com páginas demais (máximo 300)", "es")).isEqualTo("PDF con demasiadas páginas (máximo 300)");
        // O valor capturado também é traduzido quando tem entrada própria ("Grátis").
        assertThat(tradutor.traduzir("Você atingiu o limite de 20 gerações com IA deste mês no plano Grátis.", "en"))
                .isEqualTo("You've reached the limit of 20 AI generations this month on the Free plan.");
    }

    @Test
    void portuguesEMensagemDesconhecidaFicamComoEstao() {
        assertThat(tradutor.traduzir("Matéria não encontrada", "pt-BR")).isEqualTo("Matéria não encontrada");
        assertThat(tradutor.traduzir("Mensagem nova sem tradução", "en")).isEqualTo("Mensagem nova sem tradução");
        assertThat(tradutor.traduzir(null, "en")).isNull();
    }

    /**
     * Varre o código: toda mensagem literal entregue ao usuário (ResponseStatusException, message= das
     * validações, MSG_* do AuthService) precisa ter tradução em inglês e espanhol. Mensagem nova sem
     * tradução derruba este teste, em vez de aparecer em português no site em inglês.
     */
    @Test
    void todaMensagemDoCodigoTemTraducao() throws IOException {
        Pattern[] padroes = {
                Pattern.compile("ResponseStatusException\\(\\s*[^,]+,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)"),
                Pattern.compile("message\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\""),
                Pattern.compile("MSG_[A-Z_]+ =\\s*\"((?:[^\"\\\\]|\\\\.)*)\""),
                Pattern.compile("(?:erro\\(|\"erro\",\\s*)\"((?:[^\"\\\\]|\\\\.)*)\""),
        };
        List<String> semTraducao = new ArrayList<>();
        int encontradas = 0;
        try (Stream<Path> arquivos = Files.walk(Path.of("src/main/java"))) {
            for (Path p : arquivos.filter(f -> f.toString().endsWith(".java")).toList()) {
                String fonte = Files.readString(p);
                for (Pattern padrao : padroes) {
                    Matcher m = padrao.matcher(fonte);
                    while (m.find()) {
                        String msg = m.group(1).replace("\\\"", "\"");
                        encontradas++;
                        for (String idioma : List.of("en", "es")) {
                            if (tradutor.traduzir(msg, idioma).equals(msg)) semTraducao.add(idioma + ": " + msg + " (" + p.getFileName() + ")");
                        }
                    }
                }
            }
        }
        assertThat(encontradas).as("o teste precisa achar as mensagens").isGreaterThan(40);
        assertThat(semTraducao).isEmpty();
    }
}

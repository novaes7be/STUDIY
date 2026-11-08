package com.studiy.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class DivisorDeTextoTest {

    /** Junta as partes como estavam no original: entre partes inteiras volta o "\n\n" do parágrafo. */
    private static String remontar(List<DivisorDeTexto.Parte> partes, String original) {
        StringBuilder sb = new StringBuilder();
        for (DivisorDeTexto.Parte p : partes) {
            if (sb.length() > 0 && original.startsWith(sb + "\n\n")) sb.append("\n\n");
            sb.append(p.texto());
        }
        return sb.toString();
    }

    @Test
    void textoCurtoViraUmaParteSo() {
        assertThat(DivisorDeTexto.dividir("# A\n\nTexto curto.", 1000))
                .containsExactly(new DivisorDeTexto.Parte("", "# A\n\nTexto curto."));
    }

    @Test
    void nenhumCaractereSePerdeEMaximoERespeitado() {
        Random r = new Random(42);
        StringBuilder md = new StringBuilder();
        for (int cap = 1; cap <= 6; cap++) {
            md.append("# Capitulo ").append(cap).append("\n\n");
            for (int sec = 1; sec <= 4; sec++) {
                md.append("## Secao ").append(cap).append('.').append(sec).append("\n\n");
                for (int par = 0; par < 3 + r.nextInt(5); par++) {
                    int frases = 2 + r.nextInt(12);
                    for (int f = 0; f < frases; f++) md.append("Frase ").append(cap).append('-').append(sec).append('-').append(par).append('-').append(f).append(" sobre o assunto. ");
                    md.setLength(md.length() - 1);
                    md.append("\n\n");
                }
            }
        }
        md.append("Paragrafo enorme ").append("palavra ".repeat(3000)).append("fim.");
        String original = md.toString().strip();

        List<DivisorDeTexto.Parte> partes = DivisorDeTexto.dividir(original, 4000);

        assertThat(partes).hasSizeGreaterThan(5);
        assertThat(partes).allSatisfy(p -> assertThat(p.texto().length()).isLessThanOrEqualTo(4000));
        assertThat(remontar(partes, original)).isEqualTo(original);
    }

    @Test
    void parteGanhaOCaminhoDeTitulosEmQueComeca() {
        String md = "# Biologia\n\n## Mitose\n\n" + "Texto da mitose. ".repeat(40).strip()
                + "\n\n" + "Mais texto da mitose. ".repeat(40).strip();
        List<DivisorDeTexto.Parte> partes = DivisorDeTexto.dividir(md, 900);

        assertThat(partes.get(0).contexto()).isEmpty();
        assertThat(partes.get(partes.size() - 1).contexto()).isEqualTo("Biologia > Mitose");
    }
}

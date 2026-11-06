package com.studiy.backend.model;

/** Botões da revisão e a qualidade SM-2 (0 a 5) que cada um representa. A escala vive só aqui. */
public enum RespostaRevisao {
    ERREI("Errei", 1),
    DIFICIL("Difícil", 3),
    BOM("Bom", 4),
    FACIL("Fácil", 5);

    private final String rotulo;
    private final int qualidade;

    RespostaRevisao(String rotulo, int qualidade) {
        this.rotulo = rotulo;
        this.qualidade = qualidade;
    }

    public String rotulo() {
        return rotulo;
    }

    public int qualidade() {
        return qualidade;
    }
}

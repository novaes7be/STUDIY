package com.studiy.backend.i18n;

import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/**
 * Idiomas do site: pt-BR (padrão), en e es. O do pedido vem do cabeçalho Accept-Language,
 * que o front manda com o idioma da página (spring.web.locale=pt_BR quando não vem nenhum).
 */
public final class Idioma {

    public static final String PT = "pt-BR";
    public static final String EN = "en";
    public static final String ES = "es";

    private Idioma() {
    }

    /** Idioma suportado mais próximo do locale; qualquer outro vira português. */
    public static String de(Locale locale) {
        if (locale == null) return PT;
        return switch (locale.getLanguage()) {
            case "en" -> EN;
            case "es" -> ES;
            default -> PT;
        };
    }

    /** Idioma do pedido HTTP atual (fora de um pedido, português). */
    public static String doPedido() {
        return de(LocaleContextHolder.getLocale());
    }

    /** Pasta do site naquele idioma, para montar links: "" em português, "/en" e "/es" nos outros. */
    public static String pastaDoSite(String idioma) {
        return PT.equals(idioma) || idioma == null ? "" : "/" + idioma;
    }
}

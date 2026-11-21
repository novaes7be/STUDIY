package com.studiy.backend.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traduz as mensagens que a API devolve ao usuário. O texto em português continua no código (é a chave);
 * resources/i18n/api-{en,es}.json dizem como ele fica em cada idioma. Mensagens com valores usam {nome}
 * na chave: "Você atingiu o limite de {limite} gerações..." casa com o texto já montado, e cada valor
 * capturado também é traduzido se tiver entrada própria (ex.: "Grátis").
 * Sem tradução, devolve o original: uma mensagem nova nunca some, no máximo sai em português.
 */
@Component
public class TradutorMensagens {

    private static final Pattern VARIAVEL = Pattern.compile("\\{(\\w+)}");

    private record Modelo(Pattern padrao, List<String> nomes, String traducao) {}

    private final Map<String, Map<String, String>> exatas = new LinkedHashMap<>();
    private final Map<String, List<Modelo>> modelos = new LinkedHashMap<>();

    public TradutorMensagens(ObjectMapper json) {
        for (String idioma : List.of(Idioma.EN, Idioma.ES)) {
            Map<String, String> dic = ler(json, "i18n/api-" + idioma + ".json");
            Map<String, String> fixas = new LinkedHashMap<>();
            List<Modelo> comVariaveis = new ArrayList<>();
            dic.forEach((pt, traducao) -> {
                if (VARIAVEL.matcher(pt).find()) comVariaveis.add(compilar(pt, traducao));
                else fixas.put(pt, traducao);
            });
            exatas.put(idioma, fixas);
            modelos.put(idioma, comVariaveis);
        }
    }

    /** Traduz para o idioma do pedido atual. */
    public String traduzir(String mensagem) {
        return traduzir(mensagem, Idioma.doPedido());
    }

    public String traduzir(String mensagem, String idioma) {
        if (mensagem == null || !exatas.containsKey(idioma)) return mensagem;
        String exata = exatas.get(idioma).get(mensagem);
        if (exata != null) return exata;
        for (Modelo m : modelos.get(idioma)) {
            Matcher casou = m.padrao().matcher(mensagem);
            if (!casou.matches()) continue;
            String saida = m.traducao();
            for (int i = 0; i < m.nomes().size(); i++) {
                String valor = casou.group(i + 1);
                saida = saida.replace("{" + m.nomes().get(i) + "}", exatas.get(idioma).getOrDefault(valor, valor));
            }
            return saida;
        }
        return mensagem;
    }

    /** "Limite de {n} itens." vira o regex ^Limite de (.+?) itens\.$, guardando a ordem dos nomes. */
    private static Modelo compilar(String pt, String traducao) {
        StringBuilder regex = new StringBuilder();
        List<String> nomes = new ArrayList<>();
        Matcher m = VARIAVEL.matcher(pt);
        int fim = 0;
        while (m.find()) {
            regex.append(Pattern.quote(pt.substring(fim, m.start()))).append("(.+?)");
            nomes.add(m.group(1));
            fim = m.end();
        }
        regex.append(Pattern.quote(pt.substring(fim)));
        return new Modelo(Pattern.compile(regex.toString()), nomes, traducao);
    }

    private static Map<String, String> ler(ObjectMapper json, String caminho) {
        try (InputStream in = new ClassPathResource(caminho).getInputStream()) {
            return json.readValue(in, new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler " + caminho, e);
        }
    }
}

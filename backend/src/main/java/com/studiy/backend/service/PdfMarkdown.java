package com.studiy.backend.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte PDF em Markdown localmente (PDFBox, sem IA). Usa o tamanho e o peso da fonte
 * para achar títulos, reconhece listas, junta linhas quebradas em parágrafos e remove
 * cabeçalho/rodapé repetido e número de página. Esse ruído só gastava tokens.
 *
 * O texto em si nunca é descartado: só sai o que se repete em quase todas as páginas
 * (cabeçalho/rodapé) e números de página soltos.
 */
public final class PdfMarkdown {

    private static final Pattern MARCADOR_LISTA = Pattern.compile("^[•●▪◦○■□➢►▸‣⁃∙·*\\-–—]\\s+(.*)$");
    private static final Pattern LISTA_NUMERADA = Pattern.compile("^(\\d{1,3})[.)]\\s+(.*)$");
    private static final Pattern LISTA_LETRA = Pattern.compile("^([a-zA-Z])\\)\\s+(.*)$");
    private static final Pattern NUMERO_PAGINA = Pattern.compile(
            "^(p[áa]g(ina)?\\.?\\s*)?\\d{1,4}(\\s*(de|/)\\s*\\d{1,4})?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIM_DE_FRASE = Pattern.compile("[.!?:;\"”»)]$");

    private PdfMarkdown() {}

    /** Uma linha do PDF com o que importa para decidir a estrutura. */
    record Linha(String texto, float tamanho, boolean negrito, int pagina, boolean fimParagrafo) {}

    /** PDF original: usa fonte e espaçamento. */
    public static String deDocumento(PDDocument documento) throws IOException {
        Coletor coletor = new Coletor();
        coletor.setSortByPosition(true);
        coletor.writeText(documento, new StringWriter());
        coletor.fecharLinha();
        return montar(coletor.linhas, true);
    }

    /** Texto puro já extraído (PDFs antigos, sem o arquivo original): só heurísticas de texto. */
    public static String deTexto(String texto) {
        List<Linha> linhas = new ArrayList<>();
        for (String l : normalizar(texto).split("\n", -1)) {
            String t = l.strip();
            if (t.isEmpty()) {
                if (!linhas.isEmpty()) marcarFimParagrafo(linhas);
                continue;
            }
            linhas.add(new Linha(t, 0, false, 1, false));
        }
        return montar(linhas, false);
    }

    // ------------------------------------------------------------------ montagem

    static String montar(List<Linha> brutas, boolean temFonte) {
        float corpo = temFonte ? tamanhoDoCorpo(brutas) : 0;
        List<Linha> linhas = removerRuido(brutas, corpo);
        if (linhas.isEmpty()) return "";

        Map<Float, Integer> niveis = temFonte ? niveisDeTitulo(linhas, corpo) : Map.of();
        int linhaTipica = comprimentoTipico(linhas);

        List<String> blocos = new ArrayList<>();
        StringBuilder atual = null;   // parágrafo ou item de lista em construção
        boolean atualEhItem = false;
        Linha anterior = null;

        for (Linha l : linhas) {
            String t = l.texto();
            int nivel = nivelTitulo(l, corpo, niveis, temFonte);
            String item = itemDeLista(t);

            boolean continua = atual != null && nivel == 0 && item == null
                    && !quebraParagrafo(anterior, l, temFonte, linhaTipica);

            if (continua) {
                juntar(atual, t);
            } else {
                if (atual != null) blocos.add((atualEhItem ? "\u0001" : "") + atual);
                atual = null;
                atualEhItem = false;
                if (nivel > 0) {
                    blocos.add("#".repeat(nivel) + " " + t);
                } else if (item != null) {
                    atual = new StringBuilder(item);
                    atualEhItem = true;
                } else {
                    atual = new StringBuilder(t);
                }
            }
            anterior = l;
        }
        if (atual != null) blocos.add((atualEhItem ? "\u0001" : "") + atual);

        // Itens de lista seguidos ficam colados (uma lista); o resto separado por linha em branco.
        StringBuilder md = new StringBuilder();
        boolean ultimoItem = false;
        for (String b : blocos) {
            boolean ehItem = b.startsWith("\u0001");
            String texto = ehItem ? b.substring(1) : b;
            if (md.length() > 0) md.append(ehItem && ultimoItem ? "\n" : "\n\n");
            md.append(texto);
            ultimoItem = ehItem;
        }
        return md.toString().replaceAll("[ \\t]+", " ").replaceAll(" +\n", "\n").strip() + "\n";
    }

    /**
     * Remove número de página solto e cabeçalho/rodapé: linha na borda da página que se repete na
     * maioria das páginas. Com fonte, só conta se for MENOR que o corpo (título e lista repetidos
     * ficam). Sem fonte, exige repetição em 80% das páginas e só olha a primeira/última linha.
     */
    static List<Linha> removerRuido(List<Linha> linhas, float corpo) {
        boolean temFonte = corpo > 0;
        Map<Integer, List<Integer>> porPagina = new TreeMap<>();
        for (int i = 0; i < linhas.size(); i++) {
            porPagina.computeIfAbsent(linhas.get(i).pagina(), k -> new ArrayList<>()).add(i);
        }
        Set<Integer> remover = new HashSet<>();
        int paginas = porPagina.size();
        if (paginas >= 3) {
            Map<String, Set<Integer>> ocorrencias = new HashMap<>();
            for (var e : porPagina.entrySet()) {
                for (int i : bordas(e.getValue(), linhas, temFonte)) {
                    ocorrencias.computeIfAbsent(chave(linhas.get(i).texto()), k -> new HashSet<>()).add(e.getKey());
                }
            }
            int minimo = Math.max(3, (int) Math.ceil(paginas * (temFonte ? 0.5 : 0.8)));
            for (var e : porPagina.entrySet()) {
                for (int i : bordas(e.getValue(), linhas, temFonte)) {
                    Linha l = linhas.get(i);
                    String k = chave(l.texto());
                    boolean letraMiuda = !temFonte || l.tamanho() < corpo * 0.95f;
                    if (letraMiuda && !k.isBlank() && ocorrencias.get(k).size() >= minimo) remover.add(i);
                }
            }
        }
        List<Linha> limpas = new ArrayList<>();
        for (int i = 0; i < linhas.size(); i++) {
            Linha l = linhas.get(i);
            if (remover.contains(i) || NUMERO_PAGINA.matcher(l.texto()).matches()) {
                // a marca de fim de parágrafo não pode sumir junto com a linha removida
                if (l.fimParagrafo() && !limpas.isEmpty()) marcarFimParagrafo(limpas);
                continue;
            }
            limpas.add(l);
        }
        return limpas;
    }

    /** Linhas candidatas a cabeçalho/rodapé, ignorando números de página soltos. */
    private static List<Integer> bordas(List<Integer> idx, List<Linha> linhas, boolean temFonte) {
        List<Integer> uteis = idx.stream().filter(i -> !NUMERO_PAGINA.matcher(linhas.get(i).texto()).matches()).toList();
        if (uteis.isEmpty()) return uteis;
        int ultimo = uteis.size() - 1;
        if (!temFonte || uteis.size() <= 2) return uteis.size() == 1 ? uteis : List.of(uteis.get(0), uteis.get(ultimo));
        return List.of(uteis.get(0), uteis.get(1), uteis.get(ultimo - 1), uteis.get(ultimo)).stream().distinct().toList();
    }

    /** Mesma linha com outro número (ex.: "Capítulo 2 · 14") conta como repetida. */
    private static String chave(String t) {
        return t.toLowerCase(Locale.ROOT).replaceAll("\\d+", "#").replaceAll("\\s+", " ").strip();
    }

    /** Tamanho de fonte mais usado, ponderado por caracteres. */
    private static float tamanhoDoCorpo(List<Linha> linhas) {
        Map<Float, Integer> peso = new HashMap<>();
        for (Linha l : linhas) peso.merge(arredondar(l.tamanho()), l.texto().length(), Integer::sum);
        return peso.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0f);
    }

    /** Os até 3 tamanhos maiores que o corpo, do maior (#) para o menor (###). */
    private static Map<Float, Integer> niveisDeTitulo(List<Linha> linhas, float corpo) {
        TreeSet<Float> grandes = new TreeSet<>(Comparator.reverseOrder());
        for (Linha l : linhas) {
            float t = arredondar(l.tamanho());
            if (corpo > 0 && t >= corpo * 1.15f && pareceTitulo(l.texto())) grandes.add(t);
        }
        Map<Float, Integer> niveis = new HashMap<>();
        int n = 1;
        for (Float t : grandes) {
            niveis.put(t, Math.min(n++, 3));
        }
        return niveis;
    }

    private static int nivelTitulo(Linha l, float corpo, Map<Float, Integer> niveis, boolean temFonte) {
        String t = l.texto();
        if (!pareceTitulo(t) || itemDeLista(t) != null) return 0;
        if (temFonte) {
            Integer n = niveis.get(arredondar(l.tamanho()));
            if (n != null) return n;
            // negrito do tamanho do corpo, linha curta e isolada: subtítulo
            boolean corpoOuMenor = l.tamanho() <= corpo * 1.05f;
            if (l.negrito() && corpoOuMenor && t.length() <= 90 && l.fimParagrafo()) return Math.min(niveis.size() + 1, 4);
            return 0;
        }
        // Sem fonte: linha curta toda em maiúsculas vira título.
        long letras = t.chars().filter(Character::isLetter).count();
        long maiusculas = t.chars().filter(Character::isUpperCase).count();
        return letras >= 3 && t.length() <= 80 && maiusculas >= letras * 0.8 ? 2 : 0;
    }

    private static boolean pareceTitulo(String t) {
        return t.length() <= 120 && !t.endsWith(".") && !t.endsWith(",") && !t.endsWith(";")
                && t.chars().anyMatch(Character::isLetter);
    }

    /** Devolve a linha já no formato de item Markdown, ou null se não for item. */
    static String itemDeLista(String t) {
        Matcher m = MARCADOR_LISTA.matcher(t);
        if (m.matches() && !m.group(1).isBlank()) return "- " + m.group(1);
        m = LISTA_NUMERADA.matcher(t);
        if (m.matches()) return m.group(1) + ". " + m.group(2);
        m = LISTA_LETRA.matcher(t);
        if (m.matches()) return "- " + m.group(1) + ") " + m.group(2);
        return null;
    }

    private static boolean quebraParagrafo(Linha anterior, Linha atual, boolean temFonte, int linhaTipica) {
        if (anterior == null) return true;
        String prev = anterior.texto();
        boolean prevTermina = FIM_DE_FRASE.matcher(prev).find();
        boolean comecaMinuscula = Character.isLowerCase(atual.texto().codePointAt(0));
        if (anterior.fimParagrafo()) {
            // Fim de página corta parágrafo no meio da frase: junta se claramente continua.
            boolean trocouPagina = anterior.pagina() != atual.pagina();
            return !(trocouPagina && !prevTermina && comecaMinuscula);
        }
        if (temFonte) return false; // o PDFBox já marca os parágrafos pelo espaçamento
        // Texto puro: linha que termina frase e é bem mais curta que as outras fecha o parágrafo.
        return prevTermina && prev.length() < linhaTipica * 0.8 && !comecaMinuscula;
    }

    private static void juntar(StringBuilder sb, String proxima) {
        int n = sb.length();
        boolean hifen = n > 1 && sb.charAt(n - 1) == '-' && Character.isLetter(sb.charAt(n - 2))
                && Character.isLowerCase(proxima.codePointAt(0));
        if (hifen) {
            sb.setLength(n - 1); // "concei-\nto" -> "conceito"
            sb.append(proxima);
        } else {
            sb.append(' ').append(proxima);
        }
    }

    private static int comprimentoTipico(List<Linha> linhas) {
        int[] tam = linhas.stream().mapToInt(l -> l.texto().length()).filter(x -> x > 20).sorted().toArray();
        return tam.length == 0 ? 80 : tam[(int) (tam.length * 0.75)];
    }

    private static void marcarFimParagrafo(List<Linha> linhas) {
        Linha u = linhas.get(linhas.size() - 1);
        linhas.set(linhas.size() - 1, new Linha(u.texto(), u.tamanho(), u.negrito(), u.pagina(), true));
    }

    private static float arredondar(float t) {
        return Math.round(t * 2) / 2f;
    }

    static String normalizar(String s) {
        return s.replace("\r\n", "\n").replace('\r', '\n')
                .replace("­", "")      // hífen opcional
                .replace(' ', ' ')     // espaço inquebrável
                .replace("ﬁ", "fi").replace("ﬂ", "fl").replace("ﬀ", "ff").replace("ﬃ", "ffi").replace("ﬄ", "ffl");
    }

    // ------------------------------------------------------------------ coleta (PDFBox)

    /** Captura cada linha com tamanho médio de fonte, negrito e página, em vez de escrever texto. */
    private static final class Coletor extends PDFTextStripper {
        final List<Linha> linhas = new ArrayList<>();
        private final StringBuilder texto = new StringBuilder();
        private float somaTamanho;
        private int caracteres;
        private int negritos;

        Coletor() throws IOException {
            super();
        }

        @Override
        protected void writeString(String s, List<TextPosition> posicoes) {
            texto.append(s);
            for (TextPosition p : posicoes) {
                if (p.getUnicode() == null || p.getUnicode().isBlank()) continue;
                somaTamanho += p.getFontSizeInPt();
                caracteres++;
                if (ehNegrito(p.getFont())) negritos++;
            }
        }

        @Override
        protected void writeWordSeparator() {
            texto.append(' ');
        }

        @Override
        protected void writeLineSeparator() {
            fecharLinha();
        }

        @Override
        protected void writeParagraphStart() {
            // nada: o parágrafo é marcado no fim
        }

        @Override
        protected void writeParagraphEnd() {
            fecharLinha();
            if (!linhas.isEmpty()) marcarFimParagrafo(linhas);
        }

        @Override
        protected void writePageStart() {
            // sem separador de página no Markdown
        }

        @Override
        protected void writePageEnd() {
            fecharLinha();
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            fecharLinha();
            super.endPage(page);
        }

        void fecharLinha() {
            String t = normalizar(texto.toString()).strip();
            if (!t.isEmpty()) {
                float tamanho = caracteres == 0 ? 0 : somaTamanho / caracteres;
                boolean negrito = caracteres > 0 && negritos >= caracteres * 0.8;
                linhas.add(new Linha(t, tamanho, negrito, getCurrentPageNo(), false));
            }
            texto.setLength(0);
            somaTamanho = 0;
            caracteres = 0;
            negritos = 0;
        }

        private static boolean ehNegrito(PDFont fonte) {
            if (fonte == null) return false;
            PDFontDescriptor d = fonte.getFontDescriptor();
            if (d != null && (d.isForceBold() || d.getFontWeight() >= 600)) return true;
            String nome = fonte.getName() == null ? "" : fonte.getName().toLowerCase(Locale.ROOT);
            return nome.contains("bold") || nome.contains("black") || nome.contains("heavy") || nome.contains("semibold");
        }
    }
}

package com.vttcaseiro.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Busca unica (Ctrl+K): uma lista de coisas da mesa (fichas, monstros, cenas, pins, diario, campanha, compendio)
 * filtrada pelo que foi digitado. Sem acento e sem maiuscula; o nome que comeca com o texto vem antes do que so
 * tem uma palavra comecando com ele, que vem antes do que so contem. Empate: a ordem dos tipos e depois o nome.
 */
public final class GlobalSearch {
    private GlobalSearch() {
    }

    /** Tipos, na ordem em que aparecem quando empatam. */
    public enum Kind {
        PIN("Lugar nesta cena"), CENA("Cena"), FICHA("Ficha"), MONSTRO("Monstro / PdM"), DIARIO("Diário"),
        PDM("PdM da campanha"), FACCAO("Facção"), MISSAO("Missão"), COMPENDIO("Compêndio");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** Uma coisa achavel: tipo, nome, detalhe curto (aparece ao lado) e o objeto que o Main sabe abrir. */
    public record Item(Kind kind, String title, String detail, Object payload) {
    }

    /** Ate {@code max} itens que batem com {@code query}, os melhores primeiro. Texto vazio: nada. */
    public static List<Item> filter(List<Item> items, String query, int max) {
        String q = fold(query);
        List<Item> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        List<Object[]> scored = new ArrayList<>();
        for (Item it : items) {
            int s = score(fold(it.title()), q);
            if (s > 0) scored.add(new Object[]{s, it});
        }
        scored.sort(Comparator.<Object[]>comparingInt(a -> -(int) a[0])
                .thenComparingInt(a -> ((Item) a[1]).kind().ordinal())
                .thenComparing(a -> fold(((Item) a[1]).title())));
        for (Object[] a : scored) {
            if (out.size() >= max) break;
            out.add((Item) a[1]);
        }
        return out;
    }

    /** 3 = nome comeca com o texto, 2 = alguma palavra comeca, 1 = contem, 0 = nao bate. */
    static int score(String title, String q) {
        if (title.startsWith(q)) return 3;
        int i = title.indexOf(q);
        if (i < 0) return 0;
        while (i >= 0) {
            if (i == 0 || !Character.isLetterOrDigit(title.charAt(i - 1))) return 2;
            i = title.indexOf(q, i + 1);
        }
        return 1;
    }

    /** Sem acento, minusculo, sem espacos nas pontas. */
    public static String fold(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
    }
}

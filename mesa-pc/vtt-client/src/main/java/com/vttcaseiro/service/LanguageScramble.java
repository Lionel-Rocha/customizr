package com.vttcaseiro.service;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;

/**
 * Fala num idioma do jogo no chat: quem conhece o idioma (na ficha) le o texto; quem nao conhece ve as palavras
 * embaralhadas -- sempre do mesmo jeito pra mesma palavra (da pra perceber que repetiram um nome).
 */
public final class LanguageScramble {
    private LanguageScramble() {
    }

    private static final String VOWELS = "aeiouy";
    private static final String CONSONANTS = "bcdfghjklmnprstvwxz";

    /** "Ola, amigo!" -> "Ezu, ivoshe!" (mesmo tamanho, pontuacao e maiusculas mantidas). */
    public static String scramble(String text, String language) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length());
        StringBuilder word = new StringBuilder();
        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : ' ';
            if (Character.isLetter(c)) {
                word.append(c);
                continue;
            }
            if (word.length() > 0) {
                out.append(scrambleWord(word.toString(), language));
                word.setLength(0);
            }
            if (i < text.length()) out.append(c);
        }
        return out.toString();
    }

    private static String scrambleWord(String w, String language) {
        long seed = (normalize(w) + "|" + normalize(language)).hashCode();
        java.util.Random r = new java.util.Random(seed);
        StringBuilder b = new StringBuilder(w.length());
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            boolean vowel = VOWELS.indexOf(Character.toLowerCase(stripAccent(c))) >= 0;
            String pool = vowel ? VOWELS : CONSONANTS;
            char n = pool.charAt(r.nextInt(pool.length()));
            b.append(Character.isUpperCase(c) ? Character.toUpperCase(n) : n);
        }
        return b.toString();
    }

    /** O jogador conhece esse idioma? (sem acento nem maiuscula: "Élfico" = "elfico") */
    public static boolean knows(Collection<String> languages, String language) {
        if (languages == null || language == null) return false;
        String want = normalize(language);
        for (String l : languages) {
            if (l == null) continue;
            for (String part : l.split("[,;/]")) if (normalize(part).equals(want)) return true;
        }
        return false;
    }

    static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
    }

    private static char stripAccent(char c) {
        String n = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.isEmpty() ? c : n.charAt(0);
    }
}

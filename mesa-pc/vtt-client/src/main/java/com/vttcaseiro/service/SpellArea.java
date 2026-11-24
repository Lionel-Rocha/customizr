package com.vttcaseiro.service;

import com.vttcaseiro.character.PreparedSpell;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Area de uma magia lida do texto ("Self (15-foot cone)", "20-foot-radius Sphere", "cone de 4,5 m", "esfera de 6 m
 * de raio"). A forma usa os nomes da ferramenta de area do mapa: Circulo (raio), Cone e Linha (comprimento), Cubo (lado).
 */
public record SpellArea(String shape, int feet) {

    private static final String FT = "(?:-|\\s)?(?:foot|feet|ft\\.?)";
    private static final Pattern EN_SHAPE = Pattern.compile("(\\d+)" + FT + "(?:-|\\s)(cone|line|cube|square)");
    private static final Pattern EN_RADIUS = Pattern.compile("(\\d+)" + FT + "(?:-|\\s)radius");
    private static final Pattern EN_ROUND = Pattern.compile("(\\d+)" + FT + "(?:-|\\s)(sphere|cylinder|emanation|circle)");
    private static final Pattern PT_SHAPE = Pattern.compile(
            "(cone|linha|cubo|quadrado|esfera|cilindro|emana[cç][aã]o|c[ií]rculo|raio)\\s+de\\s+(\\d+(?:[.,]\\d+)?)\\s*(m\\b|metros?|p[ée]s|ft)");
    private static final Pattern PT_RADIUS = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(m\\b|metros?|p[ée]s)\\s+de\\s+raio");

    /** Area da magia (alcance, area guardada e anotacoes), ou null se nao tiver. */
    public static SpellArea of(PreparedSpell spell) {
        if (spell == null) return null;
        SpellArea a = parse(spell.getArea());
        if (a == null) a = parse(spell.getRange());
        if (a == null) a = parse(spell.getNotes());
        return a;
    }

    /** Le a primeira area do texto, ou null. */
    public static SpellArea parse(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.toLowerCase(Locale.ROOT);
        Matcher m = EN_SHAPE.matcher(t);
        if (m.find()) return new SpellArea(shapeOf(m.group(2)), Integer.parseInt(m.group(1)));
        m = EN_RADIUS.matcher(t);
        if (m.find()) return new SpellArea("Circulo", Integer.parseInt(m.group(1)));
        m = EN_ROUND.matcher(t);
        if (m.find()) return new SpellArea("Circulo", Integer.parseInt(m.group(1)));
        m = PT_RADIUS.matcher(t);
        if (m.find()) return new SpellArea("Circulo", feet(m.group(1), m.group(2)));
        m = PT_SHAPE.matcher(t);
        if (m.find()) return new SpellArea(shapeOf(m.group(1)), feet(m.group(2), m.group(3)));
        return null;
    }

    /** O trecho do texto com a area (pra guardar na magia), ou "" se nao achar. */
    public static String phrase(String text) {
        if (text == null) return "";
        String t = text.toLowerCase(Locale.ROOT);
        for (Pattern p : new Pattern[]{EN_SHAPE, EN_RADIUS, EN_ROUND, PT_RADIUS, PT_SHAPE}) {
            Matcher m = p.matcher(t);
            if (m.find()) {
                int end = Math.min(t.length(), m.end() + 12);
                return text.substring(m.start(), end).strip();
            }
        }
        return "";
    }

    private static String shapeOf(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        if (w.startsWith("cone")) return "Cone";
        if (w.startsWith("lin")) return "Linha";
        if (w.startsWith("cub") || w.startsWith("quadrado") || w.startsWith("square")) return "Cubo";
        return "Circulo";
    }

    private static int feet(String number, String unit) {
        double n = Double.parseDouble(number.replace(',', '.'));
        boolean meters = unit.startsWith("m");
        return (int) Math.round(meters ? n / 1.5 * 5 : n);
    }

    /** "esfera de 20 pés (6 m)", pra avisar no chat. */
    public String label() {
        String noun = switch (shape) {
            case "Cone" -> "cone";
            case "Linha" -> "linha";
            case "Cubo" -> "cubo";
            default -> "raio";
        };
        return noun + " de " + com.vttcaseiro.view.Distances.feetAndMeters(feet);
    }
}

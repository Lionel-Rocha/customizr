package com.vttcaseiro.compendium;

import com.vttcaseiro.content.ClassJsonAnalyzer;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * As partes de {@link SpellImporter} que sao logica pura -- mesma
 * divisao de {@code ClassImporter}/{@code ClassJsonAnalyzer} (ver
 * {@link BackgroundJsonAnalyzer} pro mesmo padrao). Os campos mecanicos
 * de magia do 5e.tools (time/range/duration/components) sao os mais
 * "estruturados" de todo o conteudo importado nesta rodada -- cada um
 * tem seu proprio formato de objeto, por isso cada um ganhou seu metodo
 * aqui em vez de um renderer generico (o {@link com.vttcaseiro.content.EntriesRenderer}
 * e so pro campo "entries" solto, nao pra esses campos estruturados).
 * <p>
 * De proposito ficam em INGLES (os termos do proprio 5e.tools) em vez de
 * traduzidos -- sao termos de jogo que a mesa provavelmente ja usa em
 * ingles na pratica, e traduzir mal um termo mecanico (ex.: "touch" vs
 * "toque") e pior do que deixar no original.
 */
public final class SpellJsonAnalyzer {

    private SpellJsonAnalyzer() {
    }

    /** A=Abjuration, C=Conjuration, D=Divination, E=Enchantment,
     * V=Evocation, I=Illusion, N=Necromancy, T=Transmutation --
     * convencao de codigo de escola do 5e.tools. Codigo desconhecido
     * devolve ele mesmo (sem travar). */
    public static String schoolName(String code) {
        if (code == null) {
            return "";
        }
        switch (code.toUpperCase(Locale.ROOT)) {
            case "A": return "Abjuration";
            case "C": return "Conjuration";
            case "D": return "Divination";
            case "E": return "Enchantment";
            case "V": return "Evocation";
            case "I": return "Illusion";
            case "N": return "Necromancy";
            case "T": return "Transmutation";
            default: return code;
        }
    }

    /** O campo "time" e uma lista (quase sempre de 1 elemento) de
     * {@code {"number":1,"unit":"action"}}, as vezes com "condition"
     * (reacoes sempre tem uma condicao explicando quando disparam). So
     * usa o PRIMEIRO elemento -- magia com tempo de conjuracao variavel
     * de verdade (raro) perde as opcoes extras aqui. */
    public static String castingTime(List<Object> time) {
        if (time == null || time.isEmpty() || !(time.get(0) instanceof Map<?, ?> rawFirst)) {
            return "";
        }
        @SuppressWarnings("unchecked") Map<String, Object> first = (Map<String, Object>) rawFirst;
        int number = ClassJsonAnalyzer.optionalInt(first, "number").orElse(1);
        String unit = ClassJsonAnalyzer.text(first, "unit");
        String unitText = unitName(unit, number);
        String base = number + " " + unitText;
        String condition = ClassJsonAnalyzer.text(first, "condition");
        return (condition == null || condition.isBlank()) ? base : base + ", " + condition;
    }

    private static String unitName(String unit, int number) {
        if (unit == null) {
            return "";
        }
        boolean plural = number != 1;
        switch (unit) {
            case "action": return "action";
            case "bonus": return "bonus action";
            case "reaction": return "reaction";
            case "minute": return plural ? "minutes" : "minute";
            case "hour": return plural ? "hours" : "hour";
            default: return unit;
        }
    }

    /** O campo "range" do 5e.tools, em formatos como
     * {@code {"type":"point","distance":{"type":"feet","amount":150}}}
     * (a maioria das magias) ou
     * {@code {"type":"emanation","distance":{"type":"feet","amount":10}}}
     * (area centrada em si mesmo -- emanation/cone/sphere/line/cube). */
    public static String range(Map<String, Object> range) {
        if (range == null) {
            return "";
        }
        String type = ClassJsonAnalyzer.text(range, "type");
        Map<String, Object> distance = ClassJsonAnalyzer.nestedMap(range, "distance");
        String distanceType = ClassJsonAnalyzer.text(distance, "type");

        if ("point".equals(type) || type == null) {
            return distanceText(distance, distanceType);
        }
        // emanation/cone/sphere/line/cube: area com origem em voce mesmo
        Integer amount = ClassJsonAnalyzer.optionalInt(distance, "amount").orElse(null);
        String unit = distanceType == null ? "feet" : distanceType;
        String unitWord = unit.equals("miles") ? "mile" : "foot";
        if (amount == null) {
            return "Self";
        }
        return "Self (" + amount + "-" + unitWord + " " + type + ")";
    }

    private static String distanceText(Map<String, Object> distance, String distanceType) {
        if (distanceType == null) {
            return "";
        }
        switch (distanceType) {
            case "self": return "Self";
            case "touch": return "Touch";
            case "sight": return "Sight";
            case "unlimited": return "Unlimited";
            case "feet": return ClassJsonAnalyzer.optionalInt(distance, "amount").map(a -> a + " feet").orElse("");
            case "miles": return ClassJsonAnalyzer.optionalInt(distance, "amount").map(a -> a + " miles").orElse("");
            default: return distanceType;
        }
    }

    /** O campo "duration" e uma lista (quase sempre de 1 elemento). So
     * usa o PRIMEIRO -- magia com duracao condicional de verdade (ex.:
     * "1 minuto, ou ate acertar um ataque") perde a segunda opcao aqui. */
    public static String duration(List<Object> duration) {
        if (duration == null || duration.isEmpty() || !(duration.get(0) instanceof Map<?, ?> rawFirst)) {
            return "";
        }
        @SuppressWarnings("unchecked") Map<String, Object> first = (Map<String, Object>) rawFirst;
        String type = ClassJsonAnalyzer.text(first, "type");
        if ("instant".equals(type)) {
            return "Instantaneous";
        }
        if ("permanent".equals(type)) {
            return "Until dispelled";
        }
        if (!"timed".equals(type)) {
            return type == null ? "" : type;
        }
        Map<String, Object> inner = ClassJsonAnalyzer.nestedMap(first, "duration");
        int amount = ClassJsonAnalyzer.optionalInt(inner, "amount").orElse(1);
        String unit = ClassJsonAnalyzer.text(inner, "type");
        String unitText = durationUnitName(unit, amount);
        String base = amount + " " + unitText;
        boolean concentration = Boolean.TRUE.equals(first.get("concentration"));
        return concentration ? "Concentration, up to " + base : base;
    }

    private static String durationUnitName(String unit, int amount) {
        if (unit == null) {
            return "";
        }
        boolean plural = amount != 1;
        switch (unit) {
            case "round": return plural ? "rounds" : "round";
            case "minute": return plural ? "minutes" : "minute";
            case "hour": return plural ? "hours" : "hour";
            case "day": return plural ? "days" : "day";
            default: return unit;
        }
    }

    /** Verdadeiro se QUALQUER elemento de "duration" marcar concentracao
     * -- usado pra preencher {@code CompendiumEntry.spellConcentration}
     * separado do texto formatado de {@link #duration}. */
    public static boolean concentration(List<Object> duration) {
        if (duration == null) {
            return false;
        }
        for (Object d : duration) {
            if (d instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("concentration"))) {
                return true;
            }
        }
        return false;
    }

    /** O campo "components": {@code {"v":true,"s":true,"m":"texto"}} --
     * "m" pode ser {@code true} (sem detalhe), uma String (o texto do
     * componente material) ou um objeto {@code {"text":...,"cost":...}}
     * (custo em pecas de cobre, igual o campo "value" de item -- ver
     * {@link ItemJsonAnalyzer#valueGp}; o custo numerico e ignorado aqui
     * de proposito, o texto ja menciona o valor por extenso). */
    public static String components(Map<String, Object> components) {
        if (components == null) {
            return "";
        }
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (Boolean.TRUE.equals(components.get("v"))) parts.add("V");
        if (Boolean.TRUE.equals(components.get("s"))) parts.add("S");
        Object m = components.get("m");
        if (m != null && !Boolean.FALSE.equals(m)) {
            String detail = materialDetail(m);
            parts.add(detail.isBlank() ? "M" : "M (" + detail + ")");
        }
        return String.join(", ", parts);
    }

    @SuppressWarnings("unchecked")
    private static String materialDetail(Object m) {
        if (m instanceof String s) {
            return s;
        }
        if (m instanceof Map<?, ?> rawMap) {
            Object text = ((Map<String, Object>) rawMap).get("text");
            return text == null ? "" : String.valueOf(text);
        }
        return "";
    }
}

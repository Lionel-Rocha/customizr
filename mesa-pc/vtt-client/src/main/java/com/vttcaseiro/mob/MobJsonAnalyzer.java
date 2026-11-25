package com.vttcaseiro.mob;

import com.vttcaseiro.content.ClassJsonAnalyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * As partes de {@link MobImporter} que sao logica pura -- mesma divisao
 * de {@code ClassImporter}/{@code ClassJsonAnalyzer} e dos 5 importadores
 * de compendio (ver {@code BackgroundJsonAnalyzer} pro mesmo padrao). Os
 * campos de um bloco de monstro do 5e.tools (tamanho, tipo, alinhamento,
 * CR, velocidade, salvaguarda/pericia) usam um punhado de abreviacoes de
 * uma letra so e alguns formatos de objeto alternativos -- cada um ganhou
 * seu proprio metodo aqui, igual {@code SpellJsonAnalyzer} fez pros
 * campos mecanicos de magia.
 */
public final class MobJsonAnalyzer {

    private MobJsonAnalyzer() {
    }

    /** O campo "size" e uma lista (quase sempre de 1 letra so -- dragoes
     * tem mais de uma, uma por "idade", mas o bloco de cada idade de
     * dragao e um bloco SEPARADO no 5e.tools, entao na pratica isso quase
     * nunca aparece com mais de 1 aqui). Junta com "/" se vier mais de
     * uma. */
    public static String sizeName(List<Object> codes) {
        if (codes == null || codes.isEmpty()) {
            return "Médio";
        }
        List<String> names = new ArrayList<>();
        for (Object code : codes) {
            names.add(sizeCodeName(String.valueOf(code)));
        }
        return String.join("/", names);
    }

    /** T=Tiny, S=Small, M=Medium, L=Large, H=Huge, G=Gargantuan --
     * convencao de tamanho do 5e.tools. Codigo desconhecido devolve ele
     * mesmo (sem travar). */
    public static String sizeCodeName(String code) {
        if (code == null) {
            return "Médio";
        }
        switch (code.toUpperCase(Locale.ROOT)) {
            case "T": return "Minúsculo";
            case "S": return "Pequeno";
            case "M": return "Médio";
            case "L": return "Grande";
            case "H": return "Enorme";
            case "G": return "Gigantesco";
            default: return code;
        }
    }

    /** O campo "type" vem como String simples ("beast") na maioria, ou
     * como objeto {@code {"type":"dragon","tags":["chromatic"]}} (tags
     * extras ignoradas aqui -- sao detalhe de categorizacao do site, nao
     * mudam a ficha) ou, raramente, {@code {"type":{"choose":[...]}}}
     * (ex.: titas que sao "celestial OU fiend" dependendo -- junta as
     * opcoes com "/" em vez de escolher uma arbitrariamente). */
    @SuppressWarnings("unchecked")
    public static String typeName(Object typeField) {
        if (typeField instanceof String s) {
            return s;
        }
        if (typeField instanceof Map<?, ?> rawMap) {
            Object inner = ((Map<String, Object>) rawMap).get("type");
            if (inner instanceof String s) {
                return s;
            }
            if (inner instanceof Map<?, ?> chooseMap) {
                Object choices = ((Map<String, Object>) chooseMap).get("choose");
                if (choices instanceof List<?> list) {
                    List<String> names = new ArrayList<>();
                    for (Object c : list) {
                        names.add(String.valueOf(c));
                    }
                    return String.join("/", names);
                }
            }
        }
        return "desconhecido";
    }

    /** O campo "alignment" e uma lista de codigos de 1 letra (ex.:
     * {@code ["L","G"]} = leal e bom). L=lawful, N=neutral, C=chaotic,
     * G=good, E=evil, U=unaligned, A=any alignment. */
    public static String alignmentName(List<Object> codes) {
        if (codes == null || codes.isEmpty()) {
            return "neutro";
        }
        List<String> names = new ArrayList<>();
        for (Object code : codes) {
            names.add(alignmentCodeName(String.valueOf(code)));
        }
        return String.join(" e ", names);
    }

    public static String alignmentCodeName(String code) {
        if (code == null) {
            return "neutro";
        }
        switch (code.toUpperCase(Locale.ROOT)) {
            case "L": return "leal";
            case "N": return "neutro";
            case "C": return "caotico";
            case "G": return "bom";
            case "E": return "mau";
            case "U": return "sem alinhamento";
            case "A": return "qualquer alinhamento";
            default: return code;
        }
    }

    /** O campo "cr" vem como String simples ("1/2", "10") na maioria, ou
     * como objeto {@code {"cr":"10","xpLair":7200}} (monstro com bloco de
     * covil -- o "xpLair" e ignorado aqui, ChallengeRatingTable.xpFor ja
     * calcula o XP normal a partir do CR). */
    @SuppressWarnings("unchecked")
    public static double crNumber(Object crField) {
        if (crField instanceof String s) {
            return crFraction(s);
        }
        if (crField instanceof Map<?, ?> rawMap) {
            Object inner = ((Map<String, Object>) rawMap).get("cr");
            if (inner != null) {
                return crFraction(String.valueOf(inner));
            }
        }
        return 0.25;
    }

    /** "1/8"/"1/4"/"1/2" (os 3 CRs fracionarios que existem em 5e) viram
     * 0.125/0.25/0.5; qualquer outra coisa tenta como inteiro, e cai em
     * 0.25 (CR 1/4, o "padrao" mais comum de monstro fraco) se nao
     * conseguir entender o texto -- nunca trava a importacao por um CR
     * estranho. */
    public static double crFraction(String text) {
        if (text == null || text.isBlank()) {
            return 0.25;
        }
        String trimmed = text.trim();
        switch (trimmed) {
            case "1/8": return 0.125;
            case "1/4": return 0.25;
            case "1/2": return 0.5;
            default:
                try {
                    return Double.parseDouble(trimmed);
                } catch (NumberFormatException ex) {
                    return 0.25;
                }
        }
    }

    /** O campo "hp" e {@code {"average":66,"formula":"12d8 + 12"}}. */
    public static int hpAverage(Map<String, Object> hp) {
        return ClassJsonAnalyzer.optionalInt(hp, "average").orElse(1);
    }

    public static String hpFormula(Map<String, Object> hp) {
        String formula = ClassJsonAnalyzer.text(hp, "formula");
        return formula == null ? "" : formula;
    }

    /** Uma velocidade dentro do campo "speed" (ex.: "walk", "fly") pode
     * ser um numero direto (a maioria) ou um objeto
     * {@code {"number":90,"condition":"(hover)"}} (o "condition" --
     * geralmente "só voando", "só em forma de X" -- e perdido aqui, so o
     * numero fica; quem revisar a importacao confere isso no texto
     * original se precisar da condicao exata). Ausente = 0 (o monstro nao
     * tem esse tipo de deslocamento). */
    @SuppressWarnings("unchecked")
    public static int speedValue(Map<String, Object> speed, String key) {
        if (speed == null) {
            return 0;
        }
        Object value = speed.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof Map<?, ?> rawMap) {
            Object number = ((Map<String, Object>) rawMap).get("number");
            if (number instanceof Number n) {
                return n.intValue();
            }
        }
        return 0;
    }

    /** Os campos "save" e "skill" sao {@code {"dex":"+5","wis":"+5"}} /
     * {@code {"arcana":"+3","nature":"+5"}} -- chave igual (so em
     * minusculo), valor sempre um texto com sinal. Devolve direto o
     * Map&lt;String,Integer&gt; que {@code MobSheet.getSavingThrowBonuses()}/
     * {@code getSkillBonuses()} guardam -- bonus que nao for um numero com
     * sinal valido (nunca vi isso acontecer nos dados reais, mas e
     * defensivo) e simplesmente pulado, nao trava a importacao. */
    public static Map<String, Integer> bonusMap(Map<String, Object> raw) {
        Map<String, Integer> result = new java.util.LinkedHashMap<>();
        if (raw == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            Integer bonus = parseSignedInt(String.valueOf(entry.getValue()));
            if (bonus != null) {
                result.put(entry.getKey(), bonus);
            }
        }
        return result;
    }

    private static Integer parseSignedInt(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim().replace("+", "");
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** O primeiro elemento de "ac" (quase sempre so numero nos dados
     * 2024 -- o formato antigo com {@code {"ac":X,"from":[...]}} existia
     * no PHB 2014 pra "armadura tal + escudo", mas nao apareceu em
     * nenhum monstro do XMM 2024 que eu testei; tratado de qualquer jeito
     * por seguranca). */
    @SuppressWarnings("unchecked")
    public static int armorClass(List<Object> ac) {
        if (ac == null || ac.isEmpty()) {
            return 10;
        }
        Object first = ac.get(0);
        if (first instanceof Number n) {
            return n.intValue();
        }
        if (first instanceof Map<?, ?> rawMap) {
            Object value = ((Map<String, Object>) rawMap).get("ac");
            if (value instanceof Number n) {
                return n.intValue();
            }
        }
        return 10;
    }

    /** resist/immune/vulnerable/conditionImmune/senses/languages sao
     * todos listas de texto na maioria -- MAS resist/immune/vulnerable
     * tem uns poucos casos especiais {@code {"special":"texto"}} (ex.:
     * "Damage type chosen for the Draconic Origin trait below", em
     * meio-dragoes). So pega os elementos que sao texto puro; os
     * elementos especiais (raros) ficam de fora da lista estruturada --
     * o texto completo deles geralmente ja esta nos traits em prosa de
     * qualquer jeito, essa lista e so o resumo rapido. */
    public static List<String> stringListOrEmpty(List<Object> raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (Object item : raw) {
            if (item instanceof String s) {
                result.add(s);
            }
        }
        return result;
    }
}

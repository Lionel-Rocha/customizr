package com.vttcaseiro.compendium;

import com.vttcaseiro.content.ClassJsonAnalyzer;

import java.util.Locale;
import java.util.Map;

/**
 * As partes de {@link ItemImporter} que sao logica pura -- mesma divisao
 * de {@code ClassImporter}/{@code ClassJsonAnalyzer} (ver
 * {@link BackgroundJsonAnalyzer} pro mesmo padrao).
 */
public final class ItemJsonAnalyzer {

    private ItemJsonAnalyzer() {
    }

    /** O campo "value" do 5e.tools e sempre em PECAS DE COBRE (cp), nao
     * em peso de ouro -- um Dagger custando 2 PO aparece como
     * {@code "value": 200}. Devolve em PO (dividido por 100), ou null se
     * o item nao tem preco listado. */
    public static Double valueGp(Map<String, Object> row) {
        return ClassJsonAnalyzer.optionalInt(row, "value").map(cp -> cp / 100.0).orElse(null);
    }

    /** Um item e "magico" quando tem uma raridade informada diferente de
     * "none"/"unknown"/vazia -- essa e a convencao do 5e.tools (item
     * mundano simplesmente nao tem o campo "rarity", ou tem "none"). */
    public static boolean isMagic(Map<String, Object> row) {
        String rarity = ClassJsonAnalyzer.text(row, "rarity");
        if (ClassJsonAnalyzer.isBlank(rarity)) {
            return false;
        }
        String normalized = rarity.toLowerCase(Locale.ROOT).trim();
        return !normalized.equals("none") && !normalized.equals("unknown") && !normalized.equals("varies");
    }

    /** "none"/vazio/ausente -> sem raridade nenhuma pra mostrar (item
     * mundano). Qualquer outro valor (common, uncommon, rare, very rare,
     * legendary, artifact) e devolvido como veio. */
    public static String displayRarity(Map<String, Object> row) {
        String rarity = ClassJsonAnalyzer.text(row, "rarity");
        if (ClassJsonAnalyzer.isBlank(rarity) || rarity.equalsIgnoreCase("none")) {
            return "";
        }
        return rarity;
    }

    /** O campo "reqAttune" do 5e.tools e ou um booleano, ou uma String
     * descrevendo a restricao (ex.: "by a Spellcaster") -- os dois
     * significam "precisa sintonizar", so que a String tambem carrega
     * uma restricao de quem pode. Simplificacao de proposito: "reqAttune
     * optional" (sintonizacao opcional) tambem conta como "precisa"
     * aqui -- o texto completo da restricao fica em
     * {@link #attunementNote}, pra quem ler a descricao entender o
     * detalhe. */
    public static boolean requiresAttunement(Map<String, Object> row) {
        Object value = row.get("reqAttune");
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return !String.valueOf(value).isBlank();
    }

    /** O texto da restricao de sintonizacao, pronto pra aparecer na
     * descricao (ex.: "Requer sintonizacao (by a Spellcaster)") -- vazio
     * se "reqAttune" for so {@code true} sem detalhe, ou se o item nao
     * precisar de sintonizacao. */
    public static String attunementNote(Map<String, Object> row) {
        Object value = row.get("reqAttune");
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        return "";
    }

    /** Formata um numero pro texto do chat sem ".0" sobrando quando o
     * valor e inteiro (2.0 -> "2"), mas mantendo a casa decimal quando
     * ela importa (12.5 -> "12.5", comum em peso de item pequeno como
     * Dart). Null -> string vazia. */
    public static String formatNumber(Double value) {
        if (value == null) {
            return "";
        }
        if (value == Math.floor(value) && !value.isInfinite()) {
            return String.valueOf(value.longValue());
        }
        return String.valueOf(value);
    }
}

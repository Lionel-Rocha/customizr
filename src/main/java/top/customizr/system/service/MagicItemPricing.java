package top.customizr.system.service;

import top.customizr.system.model.Rarity;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valor de item magico pela regra do Dungeon Master's Guide (2024), tabela "Magic Item Values":
 * Common 100 po, Uncommon 400, Rare 4.000, Very Rare 40.000, Legendary 200.000; artefato nao tem preco.
 * Consumivel (pocao, pergaminho, municao, explosivo) vale a metade, menos o Spell Scroll, que vale o dobro
 * do custo de escrever o pergaminho (tabela do Player's Handbook 2024). Arma ou armadura magica soma o
 * preco do item comum em que se baseia.
 */
public final class MagicItemPricing {

    private static final Map<Rarity, BigDecimal> BY_RARITY = Map.of(
            Rarity.COMMON, new BigDecimal("100"),
            Rarity.UNCOMMON, new BigDecimal("400"),
            Rarity.RARE, new BigDecimal("4000"),
            Rarity.VERYRARE, new BigDecimal("40000"),
            Rarity.LEGENDARY, new BigDecimal("200000"));

    /** Custo de escrever, por circulo (0 = truque). */
    private static final int[] SCRIBE_COST = {15, 25, 100, 150, 1000, 1500, 10000, 12500, 15000, 50000};

    private static final Pattern SPELL_SCROLL = Pattern.compile(
            "^spell scroll \\((cantrip|(?:level )?(\\d)(?:st|nd|rd|th)?(?: level)?)\\)$");

    private MagicItemPricing() {}

    /**
     * @param rarity     raridade do item
     * @param typeCode   codigo de tipo do 5etools sem a fonte ("P", "SC", "A"...), pode ser nulo
     * @param name       nome do item (pra reconhecer Spell Scroll)
     * @param baseValue  preco do item comum de base (arma/armadura), ou nulo
     * @return preco em po, ou nulo pra artefato
     */
    public static BigDecimal price(Rarity rarity, String typeCode, String name, BigDecimal baseValue) {
        BigDecimal scroll = spellScroll(name);
        if (scroll != null) return scroll;
        BigDecimal value = rarity == null ? null : BY_RARITY.get(rarity);
        if (value == null) return null;
        if (isConsumable(typeCode)) value = value.divide(BigDecimal.TWO);
        return baseValue == null ? value : value.add(baseValue);
    }

    static BigDecimal spellScroll(String name) {
        if (name == null) return null;
        Matcher m = SPELL_SCROLL.matcher(name.trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) return null;
        int level = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        return BigDecimal.valueOf(2L * SCRIBE_COST[level]);
    }

    public static boolean isConsumable(String typeCode) {
        if (typeCode == null) return false;
        String code = typeCode.split("\\|", 2)[0].trim().toUpperCase(Locale.ROOT);
        return switch (code) {
            case "P", "SC", "A", "AF", "EXP" -> true;
            default -> false;
        };
    }
}

package com.vttcaseiro.service;

import com.vttcaseiro.inventory.Currency;
import com.vttcaseiro.inventory.Money;

import java.util.ArrayList;
import java.util.List;

/** Divide as moedas de uma bolsa (o bau do grupo) igualmente entre varias; a sobra que nao divide fica onde estava. */
public final class CoinSplit {
    private CoinSplit() {
    }

    private static final Currency[] ORDER = {Currency.PLATINUM, Currency.GOLD, Currency.ELECTRUM, Currency.SILVER, Currency.COPPER};

    /** @return "12 po e 5 pp pra cada um; sobrou 1 po no baú" (ou vazio se nao tinha o que dividir) */
    public static String split(Money from, List<Money> to) {
        if (from == null || to == null || to.isEmpty()) return "";
        List<String> each = new ArrayList<>();
        List<String> left = new ArrayList<>();
        for (Currency c : ORDER) {
            int total = from.get(c);
            int share = total / to.size();
            if (share > 0) {
                for (Money m : to) m.add(c, share);
                from.subtract(c, share * to.size());
                each.add(share + " " + abbrev(c));
            }
            if (from.get(c) > 0) left.add(from.get(c) + " " + abbrev(c));
        }
        if (each.isEmpty()) return "";
        return String.join(", ", each) + " pra cada um" + (left.isEmpty() ? "" : "; ficou no baú: " + String.join(", ", left));
    }

    /** "30 po, 5 pp" (ou "nenhuma"). */
    public static String describe(Money m) {
        List<String> parts = new ArrayList<>();
        if (m != null) for (Currency c : ORDER) if (m.get(c) > 0) parts.add(m.get(c) + " " + abbrev(c));
        return parts.isEmpty() ? "nenhuma" : String.join(", ", parts);
    }

    static String abbrev(Currency c) {
        return switch (c) {
            case PLATINUM -> "pl";
            case GOLD -> "po";
            case ELECTRUM -> "pe";
            case SILVER -> "pp";
            default -> "pc";
        };
    }
}

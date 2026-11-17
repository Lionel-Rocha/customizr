package com.vttcaseiro.mob;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le o texto de uma acao de monstro (ja renderizado, ex.: "Ataque corpo a
 * corpo: +4, reach 5 ft. Acerto: 5 (1d6 + 2) Slashing damage") e tira o
 * que da pra rolar: bonus de ataque, dano principal e CD de salvaguarda.
 * Funciona com o texto 2014 ("+4 to hit") e 2024 ("Attack Roll: +4").
 */
public final class MobAttack {

    private static final Pattern BONUS = Pattern.compile(
            "(?:Ataque[^:]*:|Attack Roll:|Attack:)\\s*([+-]\\d+)|([+-]\\d+)\\s*to hit"
                    // monstro importado antes do renderer conhecer {@atkr}: "m +4, reach..."
                    + "|^\\s*(?:m|r|m,r)\\s+([+-]\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAMAGE = Pattern.compile("\\((\\d+d\\d+(?:\\s*[+-]\\s*\\d+)?)\\)");
    private static final Pattern SAVE_DC = Pattern.compile("(?:CD|DC)\\s*(\\d+)");

    private MobAttack() {
    }

    public static OptionalInt attackBonus(String text) {
        if (text == null) return OptionalInt.empty();
        Matcher m = BONUS.matcher(text);
        if (!m.find()) return OptionalInt.empty();
        String v = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
        return OptionalInt.of(Integer.parseInt(v));
    }

    /** Primeiro dano entre parenteses, pronto pro DiceRoller ("1d6+2"). */
    public static Optional<String> damage(String text) {
        if (text == null) return Optional.empty();
        Matcher m = DAMAGE.matcher(text);
        return m.find() ? Optional.of(m.group(1).replaceAll("\\s+", "")) : Optional.empty();
    }

    /** Tipo do primeiro dano: "(1d6 + 2) Slashing damage" -> "slashing". */
    public static String damageType(String text) {
        if (text == null) return null;
        Matcher m = DAMAGE.matcher(text);
        if (!m.find()) return null;
        return com.vttcaseiro.character.DamageTypes.find(
                text.substring(m.end(), Math.min(text.length(), m.end() + 30)));
    }

    public static OptionalInt saveDc(String text) {
        if (text == null) return OptionalInt.empty();
        Matcher m = SAVE_DC.matcher(text);
        return m.find() ? OptionalInt.of(Integer.parseInt(m.group(1))) : OptionalInt.empty();
    }
}

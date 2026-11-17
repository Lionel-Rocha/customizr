package com.vttcaseiro.dice;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rolagem de expressoes tipo "2d6+1d4+3" com detalhamento, igual ao
 * dice_engine.lua do projeto do Firecast (mesma logica, mesmos limites e
 * mesmas mensagens de erro, so que em Java). Sem estado, sem dependencia de
 * UI -- pode ser testado isolado, igual fizemos com o original em Lua.
 */
public final class DiceRoller {

    private static final int MAX_DICE = 100;
    private static final int MIN_SIDES = 2;
    private static final int MAX_SIDES = 1000;

    // sinal (+ ou -, opcional) seguido de um termo sem outro sinal dentro
    private static final Pattern TERM_PATTERN = Pattern.compile("([+-]?)([^+-]+)");
    /** "3d20kh1" = rola 3, fica com o maior; "2d20kl1" = fica com o menor (Ordem Paranormal). */
    private static final Pattern DICE_PATTERN = Pattern.compile("^(\\d*)d(\\d+)(?:(kh|kl)(\\d+))?$");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^\\d+$");

    private DiceRoller() {
    }

    /** Rola com vantagem: 2d20, fica com o MAIOR, soma o modificador.
     * O detalhe mostra os dois valores e marca qual foi usado, pra nao
     * sobrar duvida (ex.: "1d20 [15, 8 descartado] + 3 = 18"). */
    public static RollResult rollWithAdvantage(int modifier) {
        return rollKeepOne(modifier, true);
    }

    /** Rola com desvantagem: 2d20, fica com o MENOR, soma o modificador. */
    public static RollResult rollWithDisadvantage(int modifier) {
        return rollKeepOne(modifier, false);
    }

    private static RollResult rollKeepOne(int modifier, boolean keepHighest) {
        int a = ThreadLocalRandom.current().nextInt(1, 21);
        int b = ThreadLocalRandom.current().nextInt(1, 21);
        int kept = keepHighest ? Math.max(a, b) : Math.min(a, b);
        int discarded = keepHighest ? Math.min(a, b) : Math.max(a, b);
        int total = kept + modifier;
        String modText = modifier == 0 ? "" : (modifier > 0 ? " + " + modifier : " - " + (-modifier));
        String detail = "1d20 [" + kept + ", " + discarded + " descartado]" + modText + " = " + total;
        return new RollResult(total, detail, kept);
    }

    /**
     * Expressao de dano de critico (regra 5e: dobra os DADOS, nao o
     * modificador): "1d8+3" vira "2d8+3", "2d6+1d4+2" vira "4d6+2d4+2".
     */
    public static String doubleDice(String expression) {
        if (expression == null) return null;
        Matcher m = Pattern.compile("(\\d*)d(\\d+)").matcher(expression.replaceAll("\\s+", ""));
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int count = m.group(1).isEmpty() ? 1 : Integer.parseInt(m.group(1));
            m.appendReplacement(sb, (count * 2) + "d" + m.group(2));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Rola "4d6 descartando o menor" (metodo classico de gerar atributo). */
    public static int rollAbilityScore() {
        int[] rolls = new int[4];
        for (int i = 0; i < 4; i++) {
            rolls[i] = ThreadLocalRandom.current().nextInt(1, 7);
        }
        java.util.Arrays.sort(rolls);
        return rolls[1] + rolls[2] + rolls[3];
    }

    /**
     * Rola uma expressao como "2d6+3" ou "1d20+1d4-2".
     *
     * @throws DiceException com uma mensagem pronta para mostrar ao jogador
     *                        quando a expressao e invalida ou passa dos limites.
     */
    public static RollResult roll(String expression) throws DiceException {
        String expr = expression == null ? "" : expression.replaceAll("\\s+", "");
        if (expr.isEmpty()) {
            throw new DiceException("Digite uma expressao, ex.: 2d6+3");
        }

        int total = 0;
        boolean any = false;
        // d20 "natural" (critico/falha critica) -- so faz sentido quando o
        // PRIMEIRO dado da expressao e um 1d20 sozinho (ex.: "1d20+5").
        Integer naturalD20 = null;
        boolean firstDice = true;
        List<String> parts = new ArrayList<>();

        Matcher m = TERM_PATTERN.matcher(expr);
        int lastEnd = 0;
        while (m.find()) {
            if (m.start() != lastEnd) {
                // sobrou algo entre os termos que o padrao nao capturou (expressao mal formada)
                throw new DiceException("Expressao invalida: " + expr);
            }
            lastEnd = m.end();
            String sign = m.group(1);
            String term = m.group(2);
            int mult = "-".equals(sign) ? -1 : 1;
            String prefix = (sign.equals("-") ? "- " : (parts.isEmpty() ? "" : "+ "));

            Matcher dice = DICE_PATTERN.matcher(term);
            if (dice.matches()) {
                int count = dice.group(1).isEmpty() ? 1 : Integer.parseInt(dice.group(1));
                int sides = Integer.parseInt(dice.group(2));
                if (count < 1 || count > MAX_DICE || sides < MIN_SIDES || sides > MAX_SIDES) {
                    throw new DiceException("Limites: 1-100 dados, 2-1000 faces.");
                }
                int[] rolls = new int[count];
                for (int i = 0; i < count; i++) {
                    rolls[i] = ThreadLocalRandom.current().nextInt(1, sides + 1);
                }
                String keepMode = dice.group(3);
                int keep = keepMode == null ? count : Math.max(1, Math.min(count, Integer.parseInt(dice.group(4))));
                boolean[] kept = new boolean[count];
                if (keepMode == null) {
                    java.util.Arrays.fill(kept, true);
                } else {
                    Integer[] order = new Integer[count];
                    for (int i = 0; i < count; i++) order[i] = i;
                    boolean high = keepMode.equals("kh");
                    java.util.Arrays.sort(order, (a, b) -> high ? rolls[b] - rolls[a] : rolls[a] - rolls[b]);
                    for (int i = 0; i < keep; i++) kept[order[i]] = true;
                }
                int subtotal = 0;
                int keptValue = 0;
                for (int i = 0; i < count; i++) {
                    if (kept[i]) {
                        subtotal += rolls[i];
                        keptValue = rolls[i];
                    }
                }
                total += mult * subtotal;
                any = true;
                if (firstDice && keep == 1 && sides == 20 && mult == 1) {
                    naturalD20 = keptValue; // o d20 que valeu (critico/falha tambem na Ordem)
                }
                firstDice = false;
                StringBuilder rollsText = new StringBuilder();
                StringBuilder dropped = new StringBuilder();
                for (int i = 0; i < rolls.length; i++) {
                    StringBuilder target = kept[i] ? rollsText : dropped;
                    if (target.length() > 0) target.append(",");
                    target.append(rolls[i]);
                }
                if (dropped.length() > 0) rollsText.append(" (fora: ").append(dropped).append(")");
                parts.add(prefix + count + "d" + sides + (keepMode == null ? "" : keepMode + keep) + "[" + rollsText + "]");
            } else if (NUMBER_PATTERN.matcher(term).matches()) {
                int value = Integer.parseInt(term);
                total += mult * value;
                parts.add(prefix + term);
            } else {
                throw new DiceException("Expressao invalida: " + term);
            }
        }
        if (lastEnd != expr.length()) {
            throw new DiceException("Expressao invalida: " + expr);
        }
        if (!any) {
            throw new DiceException("Inclua ao menos um dado (ex.: 1d20).");
        }
        String detail = String.join(" ", parts) + " = " + total;
        return new RollResult(total, detail, naturalD20);
    }
}

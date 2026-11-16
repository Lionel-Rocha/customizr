package top.customizr.system.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rolagem de expressoes como "2d6+1d4+3", "1d20-1", "4d6kh3" (fica com os 3 maiores) e
 * "2d20kl1" (fica com o menor). Sem estado: calculado como o {@link SpellSlotCalculator}.
 * Expressao invalida gera {@link IllegalArgumentException} (o servico devolve 400).
 */
public final class DiceRoller {

    public static final int MAX_DICE = 100;
    public static final int MIN_SIDES = 2;
    public static final int MAX_SIDES = 1000;

    private static final Pattern TERM = Pattern.compile("([+-]?)([^+-]+)");
    private static final Pattern DICE = Pattern.compile("^(\\d*)d(\\d+)(?:(kh|kl)(\\d+))?$");
    private static final Pattern NUMBER = Pattern.compile("^\\d+$");
    private static final Pattern DICE_IN_TEXT = Pattern.compile("(\\d*)d(\\d+)");

    private DiceRoller() {}

    public static RollResult roll(String expression) {
        return roll(expression, sides -> ThreadLocalRandom.current().nextInt(1, sides + 1));
    }

    /** Igual, com o "dado" injetado (testes usam valores fixos). */
    public static RollResult roll(String expression, IntUnaryOperator die) {
        String expr = expression == null ? "" : expression.replaceAll("\\s+", "").toLowerCase();
        if (expr.isEmpty()) {
            throw new IllegalArgumentException("Dice expression is required, e.g. 2d6+3");
        }
        int total = 0;
        boolean anyDice = false;
        boolean firstDice = true;
        Integer naturalD20 = null;
        List<String> parts = new ArrayList<>();

        Matcher m = TERM.matcher(expr);
        int lastEnd = 0;
        while (m.find()) {
            if (m.start() != lastEnd) {
                throw new IllegalArgumentException("Invalid dice expression: " + expr);
            }
            lastEnd = m.end();
            boolean negative = "-".equals(m.group(1));
            String term = m.group(2);
            String prefix = negative ? "- " : (parts.isEmpty() ? "" : "+ ");

            Matcher dice = DICE.matcher(term);
            if (dice.matches()) {
                int count = dice.group(1).isEmpty() ? 1 : Integer.parseInt(dice.group(1));
                int sides = Integer.parseInt(dice.group(2));
                if (count < 1 || count > MAX_DICE || sides < MIN_SIDES || sides > MAX_SIDES) {
                    throw new IllegalArgumentException("Limits: 1-" + MAX_DICE + " dice, " + MIN_SIDES + "-" + MAX_SIDES + " sides");
                }
                int[] rolls = new int[count];
                for (int i = 0; i < count; i++) {
                    rolls[i] = die.applyAsInt(sides);
                }
                String keepMode = dice.group(3);
                int keep = keepMode == null ? count : Math.max(1, Math.min(count, Integer.parseInt(dice.group(4))));
                boolean[] kept = new boolean[count];
                if (keepMode == null) {
                    Arrays.fill(kept, true);
                } else {
                    Integer[] order = new Integer[count];
                    for (int i = 0; i < count; i++) order[i] = i;
                    boolean high = keepMode.equals("kh");
                    Arrays.sort(order, (a, b) -> high ? rolls[b] - rolls[a] : rolls[a] - rolls[b]);
                    for (int i = 0; i < keep; i++) kept[order[i]] = true;
                }
                int subtotal = 0;
                int keptValue = 0;
                StringBuilder keptText = new StringBuilder();
                StringBuilder droppedText = new StringBuilder();
                for (int i = 0; i < count; i++) {
                    StringBuilder target = kept[i] ? keptText : droppedText;
                    if (!target.isEmpty()) target.append(",");
                    target.append(rolls[i]);
                    if (kept[i]) {
                        subtotal += rolls[i];
                        keptValue = rolls[i];
                    }
                }
                if (firstDice && keep == 1 && sides == 20 && !negative) {
                    naturalD20 = keptValue;
                }
                firstDice = false;
                anyDice = true;
                total += negative ? -subtotal : subtotal;
                if (!droppedText.isEmpty()) keptText.append(" (dropped: ").append(droppedText).append(")");
                parts.add(prefix + count + "d" + sides + (keepMode == null ? "" : keepMode + keep) + "[" + keptText + "]");
            } else if (NUMBER.matcher(term).matches()) {
                int value = Integer.parseInt(term);
                total += negative ? -value : value;
                parts.add(prefix + value);
            } else {
                throw new IllegalArgumentException("Invalid dice term: " + term);
            }
        }
        if (lastEnd != expr.length()) {
            throw new IllegalArgumentException("Invalid dice expression: " + expr);
        }
        if (!anyDice) {
            throw new IllegalArgumentException("Include at least one die, e.g. 1d20");
        }
        return new RollResult(expr, total, String.join(" ", parts) + " = " + total, naturalD20);
    }

    /** Dano de critico (5e): dobra os dados, nao o modificador. "1d8+3" vira "2d8+3". */
    public static String doubleDice(String expression) {
        if (expression == null) {
            return null;
        }
        Matcher m = DICE_IN_TEXT.matcher(expression.replaceAll("\\s+", ""));
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int count = m.group(1).isEmpty() ? 1 : Integer.parseInt(m.group(1));
            m.appendReplacement(sb, (count * 2) + "d" + m.group(2));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}

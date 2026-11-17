package top.customizr.system.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Formas de gerar os seis atributos: rolar (ex.: 4d6 tirando o menor, configuravel), array padrao
 * (15, 14, 13, 12, 10, 8) e compra de pontos (27 pontos, de 8 a 15, tabela do livro).
 * Sem estado, como o {@link SpellSlotCalculator}.
 */
public final class AbilityScoreGenerator {

    public static final List<Integer> STANDARD_ARRAY = List.of(15, 14, 13, 12, 10, 8);
    public static final int POINT_BUY_BUDGET = 27;
    /** Custo de cada valor na compra de pontos (8 = 0 ... 15 = 9). */
    public static final Map<Integer, Integer> POINT_BUY_COST = Map.of(8, 0, 9, 1, 10, 2, 11, 3, 12, 4, 13, 5, 14, 7, 15, 9);

    private AbilityScoreGenerator() {}

    /** Um valor rolado: os dados (os que valeram e os descartados) e o total. */
    public record RolledScore(List<Integer> kept, List<Integer> dropped, int total) {}

    /**
     * Rola {@code sets} valores; cada um rola {@code dice} dados de {@code sides} faces e descarta os
     * {@code dropLowest} menores. {@code rerollOnesOrBelow} rola de novo os dados ate esse valor (0 = nao rola).
     */
    public static List<RolledScore> roll(int dice, int sides, int dropLowest, int sets, int rerollOnesOrBelow,
                                         IntUnaryOperator die) {
        if (dice < 1 || dice > 10) throw new IllegalArgumentException("Dice must be between 1 and 10");
        if (sides < 2 || sides > 20) throw new IllegalArgumentException("Sides must be between 2 and 20");
        if (dropLowest < 0 || dropLowest >= dice) throw new IllegalArgumentException("Drop must be less than the number of dice");
        if (sets < 1 || sets > 12) throw new IllegalArgumentException("Sets must be between 1 and 12");
        if (rerollOnesOrBelow < 0 || rerollOnesOrBelow >= sides) throw new IllegalArgumentException("Invalid reroll value");
        List<RolledScore> out = new ArrayList<>();
        for (int s = 0; s < sets; s++) {
            int[] rolls = new int[dice];
            for (int i = 0; i < dice; i++) {
                int value = die.applyAsInt(sides);
                for (int guard = 0; value <= rerollOnesOrBelow && guard < 100; guard++) value = die.applyAsInt(sides);
                rolls[i] = value;
            }
            int[] sorted = rolls.clone();
            Arrays.sort(sorted);
            List<Integer> dropped = new ArrayList<>();
            List<Integer> kept = new ArrayList<>();
            for (int i = 0; i < sorted.length; i++) (i < dropLowest ? dropped : kept).add(sorted[i]);
            out.add(new RolledScore(kept.reversed(), dropped, kept.stream().mapToInt(Integer::intValue).sum()));
        }
        return out;
    }

    /** Resultado da compra de pontos: custo, quanto sobra e o que esta errado. */
    public record PointBuyResult(int cost, int budget, int remaining, boolean valid, List<String> problems) {}

    public static PointBuyResult pointBuy(Map<AbilityScore, Integer> scores, Integer budget) {
        int limit = budget == null ? POINT_BUY_BUDGET : budget;
        List<String> problems = new ArrayList<>();
        int cost = 0;
        Map<AbilityScore, Integer> all = new LinkedHashMap<>();
        for (AbilityScore a : AbilityScore.values()) all.put(a, scores == null ? null : scores.get(a));
        for (Map.Entry<AbilityScore, Integer> e : all.entrySet()) {
            Integer value = e.getValue();
            if (value == null) {
                problems.add(e.getKey().name() + " is missing");
            } else if (!POINT_BUY_COST.containsKey(value)) {
                problems.add(e.getKey().name() + " must be between 8 and 15");
            } else {
                cost += POINT_BUY_COST.get(value);
            }
        }
        if (cost > limit) problems.add("Costs " + cost + " points, budget is " + limit);
        return new PointBuyResult(cost, limit, limit - cost, problems.isEmpty(), problems);
    }
}

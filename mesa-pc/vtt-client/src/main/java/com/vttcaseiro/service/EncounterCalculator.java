package com.vttcaseiro.service;

import java.util.List;

/**
 * Dificuldade de encontro nas regras de 2024 (Livro do Mestre): cada
 * personagem soma um orcamento de XP pelo nivel (Baixa / Moderada / Alta), e
 * o XP dos monstros e somado direto (2024 nao usa mais o multiplicador por
 * quantidade de monstros).
 */
public final class EncounterCalculator {

    private EncounterCalculator() {
    }

    /** XP por personagem: {baixa, moderada, alta}, niveis 1 a 20. */
    static final int[][] BUDGET = {
            {50, 75, 100}, {100, 150, 200}, {150, 225, 400}, {250, 375, 500}, {500, 750, 1100},
            {600, 1000, 1400}, {750, 1300, 1700}, {1000, 1700, 2100}, {1300, 2000, 2600}, {1600, 2300, 3100},
            {1900, 2900, 4100}, {2200, 3700, 4700}, {2600, 4200, 5400}, {2900, 4900, 6200}, {3300, 5400, 7800},
            {3800, 6100, 9800}, {4500, 7200, 11700}, {5000, 8700, 14200}, {5500, 10700, 17200}, {6400, 13200, 22000}};

    public enum Rating {
        TRIVIAL("Trivial (abaixo de Baixa)"),
        LOW("Baixa"),
        MODERATE("Moderada"),
        HIGH("Alta"),
        DEADLY("Acima de Alta -- pode ser mortal");

        private final String label;

        Rating(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Result(int monsterXp, int low, int moderate, int high, Rating rating) {
        public String summary() {
            return "XP dos monstros: " + monsterXp + "  |  orcamento do grupo: Baixa " + low + ", Moderada " + moderate
                    + ", Alta " + high + "  ->  " + rating.label();
        }
    }

    /** @param partyLevels nivel de cada personagem; @param monsterXp XP de cada monstro. */
    public static Result evaluate(List<Integer> partyLevels, List<Integer> monsterXp) {
        int low = 0;
        int moderate = 0;
        int high = 0;
        for (int level : partyLevels) {
            int[] b = BUDGET[Math.max(1, Math.min(20, level)) - 1];
            low += b[0];
            moderate += b[1];
            high += b[2];
        }
        int xp = 0;
        for (int m : monsterXp) xp += Math.max(0, m);
        Rating rating;
        if (xp > high) rating = Rating.DEADLY;
        else if (xp > moderate) rating = Rating.HIGH;
        else if (xp > low) rating = Rating.MODERATE;
        else if (xp >= low * 0.5) rating = Rating.LOW;
        else rating = Rating.TRIVIAL;
        if (partyLevels.isEmpty()) rating = xp == 0 ? Rating.TRIVIAL : Rating.DEADLY;
        return new Result(xp, low, moderate, high, rating);
    }
}

package com.vttcaseiro.character;

/** Como um d20 e rolado. */
public enum RollMode {
    NORMAL, ADVANTAGE, DISADVANTAGE;

    /** Regra do 5e: qualquer vantagem + qualquer desvantagem = rolagem normal,
     * nao importa quantas fontes de cada uma. */
    public static RollMode combine(boolean anyAdvantage, boolean anyDisadvantage) {
        if (anyAdvantage == anyDisadvantage) return NORMAL;
        return anyAdvantage ? ADVANTAGE : DISADVANTAGE;
    }

    public String labelPt() {
        return switch (this) {
            case NORMAL -> "";
            case ADVANTAGE -> "vantagem";
            case DISADVANTAGE -> "desvantagem";
        };
    }
}

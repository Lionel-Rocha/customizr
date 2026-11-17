package com.vttcaseiro.dice;

/**
 * Resultado de uma rolagem. {@code detail} ja vem formatado para exibir
 * ("2d6[4,5] + 3 = 12"), {@code total} e o numero puro para logica do jogo
 * (ex.: aplicar dano num token).
 */
public record RollResult(int total, String detail, Integer naturalD20) {

    public RollResult(int total, String detail) {
        this(total, detail, null);
    }

    public boolean isCriticalHit() { return naturalD20 != null && naturalD20 == 20; }

    public boolean isCriticalFail() { return naturalD20 != null && naturalD20 == 1; }

}

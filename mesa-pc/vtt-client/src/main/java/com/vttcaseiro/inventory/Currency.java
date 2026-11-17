package com.vttcaseiro.inventory;

/** As 5 moedas de D&D 5e, na ordem do valor. {@code copperValue} e quantas
 * peças de cobre uma unidade dessa moeda vale -- e o que permite somar
 * tudo numa unica base comum (ver Money.totalInCopper()) sem precisar de
 * uma tabela de conversao espalhada pelo codigo. */
public enum Currency {
    COPPER(1),
    SILVER(10),
    ELECTRUM(50),
    GOLD(100),
    PLATINUM(1000);

    private final int copperValue;

    Currency(int copperValue) {
        this.copperValue = copperValue;
    }

    public int copperValue() {
        return copperValue;
    }
}

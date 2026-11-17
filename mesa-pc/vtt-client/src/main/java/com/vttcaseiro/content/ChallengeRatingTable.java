package com.vttcaseiro.content;

import java.util.List;

/**
 * A tabela "Monster Statistics by Challenge Rating" do Dungeon Master's
 * Guide (2014), usada pelo DMG pra calcular o CR de um monstro caseiro:
 * voce calcula PV/CA esperados (CR defensivo) e dano/bonus-de-ataque
 * esperados (CR ofensivo) separadamente, acha a linha mais proxima de
 * cada um aqui, e tira a media dos dois CRs encontrados (ver
 * ChallengeRatingCalculator).
 * <p>
 * Os valores de CR 0 a 20 vem direto do livro e sao confiaveis. CR 21 a
 * 30 (poucas mesas caseiras chegam la) foram reconstruidos a partir do
 * padrao de crescimento da tabela oficial -- usaveis como estimativa,
 * mas vale conferir contra o livro se a mesa realmente for montar um
 * monstro de CR acima de 20.
 */
public final class ChallengeRatingTable {

    public static final List<CrTableRow> ROWS = List.of(
            new CrTableRow(0, 2, 13, 1, 6, 3, 0, 1, 13, 10),
            new CrTableRow(0.125, 2, 13, 7, 35, 3, 2, 3, 13, 25),
            new CrTableRow(0.25, 2, 13, 36, 49, 3, 4, 5, 13, 50),
            new CrTableRow(0.5, 2, 13, 50, 70, 3, 6, 8, 13, 100),
            new CrTableRow(1, 2, 13, 71, 85, 3, 9, 14, 13, 200),
            new CrTableRow(2, 2, 13, 86, 100, 3, 15, 20, 13, 450),
            new CrTableRow(3, 2, 13, 101, 115, 4, 21, 26, 13, 700),
            new CrTableRow(4, 2, 14, 116, 130, 5, 27, 32, 14, 1100),
            new CrTableRow(5, 3, 15, 131, 145, 6, 33, 38, 15, 1800),
            new CrTableRow(6, 3, 15, 146, 160, 7, 39, 44, 15, 2300),
            new CrTableRow(7, 3, 15, 161, 175, 8, 45, 50, 15, 2900),
            new CrTableRow(8, 3, 16, 176, 190, 9, 51, 56, 16, 3900),
            new CrTableRow(9, 4, 16, 191, 205, 10, 57, 62, 16, 5000),
            new CrTableRow(10, 4, 17, 206, 220, 11, 63, 68, 17, 5900),
            new CrTableRow(11, 4, 17, 221, 235, 12, 69, 74, 17, 7200),
            new CrTableRow(12, 4, 17, 236, 250, 13, 75, 80, 18, 8400),
            new CrTableRow(13, 5, 18, 251, 265, 13, 81, 86, 18, 10000),
            new CrTableRow(14, 5, 18, 266, 280, 14, 87, 92, 18, 11500),
            new CrTableRow(15, 5, 18, 281, 295, 14, 93, 98, 19, 13000),
            new CrTableRow(16, 5, 18, 296, 310, 15, 99, 104, 19, 15000),
            new CrTableRow(17, 6, 19, 311, 325, 15, 105, 110, 19, 18000),
            new CrTableRow(18, 6, 19, 326, 340, 16, 111, 116, 20, 20000),
            new CrTableRow(19, 6, 19, 341, 355, 16, 117, 122, 20, 22000),
            new CrTableRow(20, 6, 19, 356, 400, 17, 123, 140, 20, 25000),
            // CR 21-30: reconstruido por padrao de crescimento -- conferir contra o livro se usar a serio.
            new CrTableRow(21, 7, 19, 401, 445, 17, 141, 158, 21, 33000),
            new CrTableRow(22, 7, 19, 446, 490, 18, 159, 176, 21, 41000),
            new CrTableRow(23, 7, 19, 491, 535, 18, 177, 194, 21, 50000),
            new CrTableRow(24, 7, 19, 536, 580, 19, 195, 212, 22, 62000),
            new CrTableRow(25, 8, 19, 581, 625, 19, 213, 230, 22, 75000),
            new CrTableRow(26, 8, 19, 626, 670, 20, 231, 248, 22, 90000),
            new CrTableRow(27, 8, 19, 671, 715, 20, 249, 266, 23, 105000),
            new CrTableRow(28, 8, 19, 716, 760, 21, 267, 284, 23, 120000),
            new CrTableRow(29, 9, 19, 761, 805, 21, 285, 302, 23, 135000),
            new CrTableRow(30, 9, 19, 806, 850, 22, 303, 320, 24, 155000)
    );

    private ChallengeRatingTable() {
    }

    /** Linha cujo intervalo de PV (hpLow..hpHigh) contem o PV dado, ou a
     * linha mais proxima se estiver fora de toda a tabela (abaixo de 0 ou
     * acima de 850). */
    public static CrTableRow rowForHp(int hp) {
        for (CrTableRow row : ROWS) {
            if (hp >= row.hpLow() && hp <= row.hpHigh()) {
                return row;
            }
        }
        return hp < ROWS.get(0).hpLow() ? ROWS.get(0) : ROWS.get(ROWS.size() - 1);
    }

    /** Mesma ideia, mas pelo intervalo de dano por rodada. */
    public static CrTableRow rowForDamagePerRound(int dpr) {
        for (CrTableRow row : ROWS) {
            if (dpr >= row.damageLow() && dpr <= row.damageHigh()) {
                return row;
            }
        }
        return dpr < ROWS.get(0).damageLow() ? ROWS.get(0) : ROWS.get(ROWS.size() - 1);
    }

    /** Linha de um CR exato (0.125 pra 1/8, etc). Null se o CR nao existir
     * na tabela. */
    public static CrTableRow rowForCr(double cr) {
        for (CrTableRow row : ROWS) {
            if (Double.compare(row.cr(), cr) == 0) {
                return row;
            }
        }
        return null;
    }

    public static int indexOf(CrTableRow row) {
        return ROWS.indexOf(row);
    }

    public static int xpFor(double cr) {
        CrTableRow row = rowForCr(cr);
        return row != null ? row.xp() : 0;
    }

    /** Formata um CR double no texto que a mesa usa ("1/8", "1/4", "1/2"
     * ou um numero inteiro) -- igual a ficha oficial escreve. */
    public static String format(double cr) {
        if (cr == 0.125) return "1/8";
        if (cr == 0.25) return "1/4";
        if (cr == 0.5) return "1/2";
        return String.valueOf((int) Math.round(cr));
    }
}

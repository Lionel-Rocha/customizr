package com.vttcaseiro.content;

/**
 * Implementa o algoritmo do DMG pra sugerir o Challenge Rating de um
 * monstro caseiro a partir dos numeros dele (PV, CA, dano por rodada,
 * bonus de ataque/CD de salvaguarda). O livro descreve isso em texto
 * corrido (capitulo "Creating a Monster") -- aqui vira codigo:
 * <ol>
 *   <li>CR defensivo: acha a linha da tabela pelo PV, ajusta pra cima/baixo
 *       conforme a CA do monstro for mais alta/baixa que a esperada
 *       naquele CR (2 pontos de CA = 1 "passo" na tabela);</li>
 *   <li>CR ofensivo: mesma ideia, pelo dano por rodada, ajustado pelo
 *       bonus de ataque (ou CD de salvaguarda, o que for maior ofensivamente);</li>
 *   <li>CR final: media dos dois, tirada pelo INDICE na tabela (nao pelo
 *       valor numerico do CR) -- se tirassemos a media dos valores crus,
 *       1/4 (0.25) + 1 (1.0) dividido por 2 daria 0.625, que nao e CR
 *       nenhum da tabela. Pelo indice, 1/4 e indice 2, CR 1 e indice 4,
 *       media de indice = 3 = CR 1/2 -- um CR valido.</li>
 * </ol>
 */
public final class ChallengeRatingCalculator {

    private ChallengeRatingCalculator() {
    }

    /** CR defensivo sugerido a partir de PV e CA efetivos do monstro. */
    public static double estimateDefensiveCr(int effectiveHp, int armorClass) {
        CrTableRow baseline = ChallengeRatingTable.rowForHp(effectiveHp);
        int acDiff = armorClass - baseline.armorClass();
        int step = Math.round(acDiff / 2.0f);
        return stepRow(baseline, step).cr();
    }

    /** CR ofensivo sugerido a partir do dano medio por rodada e do melhor
     * entre bonus de ataque e CD de salvaguarda do monstro. */
    public static double estimateOffensiveCr(int damagePerRound, int attackBonusOrSaveDc) {
        CrTableRow baseline = ChallengeRatingTable.rowForDamagePerRound(damagePerRound);
        // attackBonusOrSaveDc pode vir como bonus de ataque (comparar com
        // attackBonus() da linha) ou como CD de salvaguarda (comparar com
        // saveDc()) -- quem chama decide qual dos dois esta mandando, e
        // ajusta pelo que for mais relevante pra esse monstro.
        int diff = attackBonusOrSaveDc - baseline.attackBonus();
        int step = Math.round(diff / 2.0f);
        return stepRow(baseline, step).cr();
    }

    /** CR final: media (por indice na tabela) do CR defensivo e ofensivo. */
    public static double estimateCr(int effectiveHp, int armorClass, int damagePerRound, int attackBonusOrSaveDc) {
        double defensive = estimateDefensiveCr(effectiveHp, armorClass);
        double offensive = estimateOffensiveCr(damagePerRound, attackBonusOrSaveDc);
        return averageCr(defensive, offensive);
    }

    /** Media de dois CRs pelo INDICE deles na tabela (ver javadoc da classe
     * pra entender por que isso e necessario em vez de so somar e dividir
     * por 2 os valores de CR). */
    public static double averageCr(double crA, double crB) {
        int indexA = ChallengeRatingTable.indexOf(ChallengeRatingTable.rowForCr(crA));
        int indexB = ChallengeRatingTable.indexOf(ChallengeRatingTable.rowForCr(crB));
        int avgIndex = Math.round((indexA + indexB) / 2.0f);
        avgIndex = Math.max(0, Math.min(ChallengeRatingTable.ROWS.size() - 1, avgIndex));
        return ChallengeRatingTable.ROWS.get(avgIndex).cr();
    }

    /** Anda "step" linhas na tabela a partir de baseline, sem passar dos
     * limites (CR 0 no minimo, CR 30 no maximo). */
    private static CrTableRow stepRow(CrTableRow baseline, int step) {
        int index = ChallengeRatingTable.indexOf(baseline) + step;
        index = Math.max(0, Math.min(ChallengeRatingTable.ROWS.size() - 1, index));
        return ChallengeRatingTable.ROWS.get(index);
    }
}

package com.vttcaseiro.content;

/**
 * Uma linha da tabela oficial "Monster Statistics by Challenge Rating" do
 * Dungeon Master's Guide. E a tabela que o proprio DMG manda usar depois
 * de calcular CR defensivo/ofensivo "no bruto" -- ela empurra o resultado
 * pro CR real mais proximo considerando que PV/CA/dano/bonus de ataque
 * nao sobem de forma perfeitamente linear.
 *
 * @param cr               valor do Challenge Rating (0, 0.125=1/8, 0.25=1/4, 0.5=1/2, 1, 2, ...)
 * @param proficiencyBonus bonus de proficiencia esperado nesse CR
 * @param armorClass       CA esperada nesse CR
 * @param hpLow            PV esperado minimo nesse CR
 * @param hpHigh           PV esperado maximo nesse CR
 * @param attackBonus      bonus de ataque esperado (ofensivo)
 * @param damageLow        dano por rodada esperado minimo
 * @param damageHigh       dano por rodada esperado maximo
 * @param saveDc           CD de salvaguarda esperada (ofensivo, alternativa ao bonus de ataque)
 * @param xp               experiencia concedida por derrotar um monstro desse CR
 */
public record CrTableRow(double cr, int proficiencyBonus, int armorClass, int hpLow, int hpHigh,
                          int attackBonus, int damageLow, int damageHigh, int saveDc, int xp) {

    public int hpMid() {
        return (hpLow + hpHigh) / 2;
    }

    public int damageMid() {
        return (damageLow + damageHigh) / 2;
    }
}

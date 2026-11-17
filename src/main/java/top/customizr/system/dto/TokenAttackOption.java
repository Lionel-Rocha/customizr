package top.customizr.system.dto;

import top.customizr.system.model.MonsterAttacks;
import top.customizr.system.model.SheetAttack;

/**
 * Ataque que o token pode fazer. id: o id do ataque da ficha (personagem) ou "m:N" (acao N do monstro).
 * kind: ATTACK (d20 contra a CA) ou SAVE (o alvo faz salvaguarda contra saveDc).
 */
public record TokenAttackOption(String id, String name, String kind, Integer attackBonus, Integer saveDc,
                                String saveAbility, String damage, String damageType, String text) {

    public static TokenAttackOption from(SheetAttack a) {
        return new TokenAttackOption(a.getId().toString(), a.getName(), "ATTACK", a.getAttackBonus(), null, null,
                a.getDamage(), a.getDamageType(), a.getNotes());
    }

    public static TokenAttackOption from(MonsterAttacks.Attack a) {
        return new TokenAttackOption("m:" + a.index(), a.name(), a.kind().name(), a.attackBonus(), a.saveDc(),
                a.saveAbility(), a.damage(), a.damageType(), a.text());
    }
}

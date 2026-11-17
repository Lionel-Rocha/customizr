package top.customizr.system.dto;

/**
 * Acao da ficha de monstro customizada.
 * kind: ATTACK (bonus + dano contra a CA), SAVE (CD + atributo + dano) ou OTHER (so o texto).
 * melee: corpo a corpo (true) ou a distancia (false); reach: "reach 5 ft." / "range 80/320 ft.".
 * saveAbility: STR, DEX, CON, INT, WIS ou CHA. damage: expressao de dados, ex.: "1d6+2".
 */
public record MonsterAction(String name, String kind, Integer attackBonus, Boolean melee, String reach,
                            Integer saveDc, String saveAbility, String damage, String damageType, String text) {}

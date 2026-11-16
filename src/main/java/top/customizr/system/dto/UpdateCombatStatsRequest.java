package top.customizr.system.dto;

/** Campos null ficam como estao. */
public record UpdateCombatStatsRequest(
        Integer hpMax,
        Integer hpCurrent,
        Integer hpTemp,
        Integer armorClass,
        Integer speedFt
) {}

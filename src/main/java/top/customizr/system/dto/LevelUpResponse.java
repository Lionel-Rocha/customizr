package top.customizr.system.dto;

/**
 * Nivel aplicado. {@code hpGained} ja inclui {@code retroactiveHp} (CON que subiu vale pra todos os niveis anteriores).
 * {@code hpRoll}: a rolagem, quando o servidor rolou (ex.: "1d10[7]+2").
 */
public record LevelUpResponse(LevelUpPreviewResponse applied, int hpGained, int retroactiveHp, String hpRoll,
                              String subclassName, String featName, CharacterSheetResponse sheet) {}

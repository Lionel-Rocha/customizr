package top.customizr.system.dto;

import top.customizr.system.model.AbilityScore;
import top.customizr.system.model.RollMode;
import top.customizr.system.model.Skill;

/** skill pra SKILL; ability pra ABILITY/SAVE. toChat = manda o resultado pro chat da campanha. */
public record SheetRollRequest(RollKind kind, Skill skill, AbilityScore ability, RollMode mode, Boolean toChat) {

    public enum RollKind { ABILITY, SAVE, SKILL, INITIATIVE, DEATH_SAVE }
}

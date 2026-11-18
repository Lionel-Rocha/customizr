package top.customizr.system.dto;

import top.customizr.system.model.AbilityScore;
import top.customizr.system.model.RollMode;
import top.customizr.system.model.Skill;

/**
 * skill pra SKILL; ability pra ABILITY/SAVE/TOOL; tool pra TOOL (o nome da ferramenta: soma a proficiencia se a ficha
 * tiver). toChat = manda o resultado pro chat da campanha.
 * heroicInspiration = gasta a Inspiracao Heroica nesta rolagem (rolar de novo um teste que acabou de sair).
 */
public record SheetRollRequest(RollKind kind, Skill skill, AbilityScore ability, RollMode mode, Boolean toChat,
                               Boolean heroicInspiration, String tool) {

    public SheetRollRequest(RollKind kind, Skill skill, AbilityScore ability, RollMode mode, Boolean toChat) {
        this(kind, skill, ability, mode, toChat, null, null);
    }

    public SheetRollRequest(RollKind kind, Skill skill, AbilityScore ability, RollMode mode, Boolean toChat,
                            Boolean heroicInspiration) {
        this(kind, skill, ability, mode, toChat, heroicInspiration, null);
    }

    public enum RollKind { ABILITY, SAVE, SKILL, INITIATIVE, DEATH_SAVE, TOOL }
}

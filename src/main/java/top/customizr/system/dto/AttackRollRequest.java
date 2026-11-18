package top.customizr.system.dto;

import top.customizr.system.model.RollMode;

/**
 * targetArmorClass opcional: diz se acertou. toChat = manda pro chat da campanha.
 * heroicInspiration = gasta a Inspiracao Heroica nesta rolagem (rolar o ataque de novo).
 */
public record AttackRollRequest(RollMode mode, Integer targetArmorClass, Boolean toChat, Boolean heroicInspiration) {

    public AttackRollRequest(RollMode mode, Integer targetArmorClass, Boolean toChat) {
        this(mode, targetArmorClass, toChat, null);
    }
}

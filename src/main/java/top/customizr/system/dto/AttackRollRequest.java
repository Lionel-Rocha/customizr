package top.customizr.system.dto;

import top.customizr.system.model.RollMode;

/** targetArmorClass opcional: diz se acertou. toChat = manda pro chat da campanha. */
public record AttackRollRequest(RollMode mode, Integer targetArmorClass, Boolean toChat) {}

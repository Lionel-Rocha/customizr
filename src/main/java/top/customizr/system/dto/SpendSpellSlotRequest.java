package top.customizr.system.dto;

import top.customizr.system.model.SpellSlotPool;

/** pool null = STANDARD. */
public record SpendSpellSlotRequest(SpellSlotPool pool, int level) {}

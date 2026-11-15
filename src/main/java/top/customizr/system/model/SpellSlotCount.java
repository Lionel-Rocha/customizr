package top.customizr.system.model;

public record SpellSlotCount(
        SpellSlotPool pool,
        String className,
        int spellLevel,
        int totalSlots
) {
    public SpellSlotCount {
        if (pool == null || spellLevel < 1 || spellLevel > 9 || totalSlots < 1) {
            throw new IllegalArgumentException("Invalid spell slot count");
        }
        if (pool == SpellSlotPool.STANDARD && className != null) {
            throw new IllegalArgumentException("Standard slots are shared across classes");
        }
        if (pool == SpellSlotPool.PACT && (className == null || className.isBlank())) {
            throw new IllegalArgumentException("Pact slots must identify their class");
        }
    }
}

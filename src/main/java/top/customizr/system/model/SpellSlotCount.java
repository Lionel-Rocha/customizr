package top.customizr.system.model;

public record SpellSlotCount(
        SpellSlotPool pool,
        String className,
        int spellLevel,
        int totalSlots
) {
    public SpellSlotCount {
        if (pool == null || spellLevel < 1 || spellLevel > 9 || totalSlots < 1) {
            throw new IllegalArgumentException("Quantidade de espaços de magia inválida");
        }
        if (pool == SpellSlotPool.STANDARD && className != null) {
            throw new IllegalArgumentException("Espaços comuns são compartilhados entre as classes");
        }
        if (pool == SpellSlotPool.PACT && (className == null || className.isBlank())) {
            throw new IllegalArgumentException("Espaços de pacto precisam dizer a classe");
        }
    }
}

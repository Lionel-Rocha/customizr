package top.customizr.system.dto;

import top.customizr.system.model.DndClass;

import java.util.Map;
import java.util.UUID;

public record DndClassResponse(
        UUID classId,
        String name,
        String source,
        String edition,
        Integer page,
        Boolean srd,
        int hitDieNumber,
        int hitDie,
        int subclassUnlockLevel,
        String spellcastingAbility,
        String casterProgression,
        String preparedSpells,
        String preparedSpellsChange,
        Map<String, Object> metadata
) {
    public static DndClassResponse from(DndClass dndClass) {
        return new DndClassResponse(
                dndClass.getClassId(),
                dndClass.getClassName(),
                dndClass.getSourceCode(),
                dndClass.getEdition(),
                dndClass.getPage(),
                dndClass.getSrd(),
                dndClass.getHitDieNumber(),
                dndClass.getHitDie(),
                dndClass.getSubclassUnlockLevel(),
                dndClass.getSpellcastingAbility(),
                dndClass.getCasterProgression(),
                dndClass.getPreparedSpells(),
                dndClass.getPreparedSpellsChange(),
                dndClass.getMetadata()
        );
    }
}

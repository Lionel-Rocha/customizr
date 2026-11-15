package top.customizr.system.dto;

import java.util.UUID;

public record SpellCreationRequest(
        String name,
        UUID sourceId,
        String spellSchool,
        String level,
        String castingTime,
        int rangeFt,
        String[] components,
        SpellDurationRequest duration,
        String description
) {
}

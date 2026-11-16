package top.customizr.system.dto;

import top.customizr.system.model.EncyclopediaEntry;
import top.customizr.system.model.EntryType;
import top.customizr.system.model.ReferenceType;

import java.util.UUID;

public record EncyclopediaEntryResponse(
        UUID id,
        String entryName,
        String information,
        EntryType type,
        boolean visible,
        ReferenceType referenceType,
        UUID referenceId,
        String referenceName
) {
    public static EncyclopediaEntryResponse from(EncyclopediaEntry entry) {
        return new EncyclopediaEntryResponse(entry.getId(), entry.getEntryName(), entry.getInformation(),
                entry.getType(), entry.isVisible(), entry.getReferenceType(), entry.getReferenceId(),
                entry.getReferenceName());
    }
}

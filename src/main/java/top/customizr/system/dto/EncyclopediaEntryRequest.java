package top.customizr.system.dto;

import top.customizr.system.model.EntryType;
import top.customizr.system.model.ReferenceType;

import java.util.UUID;

/** visible null = true. referenceType + referenceId opcionais (ligam a entrada a um NPC, pais, cidade ou ponto). */
public record EncyclopediaEntryRequest(
        String entryName,
        String information,
        EntryType type,
        Boolean visible,
        ReferenceType referenceType,
        UUID referenceId
) {}

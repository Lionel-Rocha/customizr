package top.customizr.system.dto;

import java.util.UUID;

public record AzgaarImportResponse(
        UUID worldId,
        String worldName,
        int countries,
        int cities,
        int skippedCities,
        int encyclopediaEntries
) {}

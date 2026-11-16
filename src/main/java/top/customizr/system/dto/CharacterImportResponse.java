package top.customizr.system.dto;

import java.util.List;
import java.util.UUID;

/** notes: o que conferir (CA que nao veio, classe ainda nao importada etc.). */
public record CharacterImportResponse(
        UUID characterId,
        String name,
        String format,
        List<String> classesAdded,
        List<String> notes
) {}

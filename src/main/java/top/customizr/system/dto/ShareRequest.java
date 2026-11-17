package top.customizr.system.dto;

import java.util.UUID;

/** kind + refId = o que mandar pro chat. characterId (opcional) = falar como esse personagem. */
public record ShareRequest(SharedKind kind, UUID refId, UUID characterId) {}

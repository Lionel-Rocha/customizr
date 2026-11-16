package top.customizr.system.dto;

import java.util.UUID;

/** characterId opcional: fala "como" esse personagem (tem que ser seu, ou voce e o mestre). */
public record ChatMessageRequest(String text, UUID characterId) {}

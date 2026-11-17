package top.customizr.system.dto;

import java.util.UUID;

/** Copia um monstro (do catalogo ou da campanha) para os monstros customizados da campanha; name opcional. */
public record MonsterCopyRequest(UUID campaignId, String name) {}

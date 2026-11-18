package top.customizr.system.dto;

import java.util.UUID;

/** Alfinete: posicao (pixels do mapa), nome, icone (emoji), nota, verbete da enciclopedia e se os jogadores veem. */
public record PinRequest(Double x, Double y, String label, String icon, String note, UUID entryId, Boolean visibleToPlayers) {}

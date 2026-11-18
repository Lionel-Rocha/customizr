package top.customizr.system.dto;

/** Luz do token (forte e fraca, em pes: tocha 20 + 20) e visao no escuro (pes). 0 ou vazio = nada. */
public record TokenLightRequest(Integer brightFt, Integer dimFt, Integer darkvisionFt) {}

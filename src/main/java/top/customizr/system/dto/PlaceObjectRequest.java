package top.customizr.system.dto;

/** Onde por o objeto (canto de cima a esquerda, em pixels do mapa), giro em graus e se fica oculto dos jogadores. */
public record PlaceObjectRequest(Double x, Double y, Integer rotation, Boolean hidden) {}

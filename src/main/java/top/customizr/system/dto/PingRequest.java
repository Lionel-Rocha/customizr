package top.customizr.system.dto;

/** Ping no mapa (x, y em pixels). focus: o mestre puxa a tela de todos pra esse ponto. */
public record PingRequest(Double x, Double y, Boolean focus) {}

package top.customizr.system.dto;

/** worldMapX/Y: posicao no mapa do mundo, relativa (0 a 1). */
public record CountryRequest(String name, String info, String masterInfo, String mapUrl, Float worldMapX, Float worldMapY) {}

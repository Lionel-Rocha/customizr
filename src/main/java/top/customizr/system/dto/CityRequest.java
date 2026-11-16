package top.customizr.system.dto;

/** countryMapX/Y: posicao no mapa do pais, relativa (0 a 1). */
public record CityRequest(String name, String info, String cityMapUrl, Float countryMapX, Float countryMapY,
                          Integer population, Boolean capital) {}

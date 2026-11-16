package top.customizr.system.dto;

import top.customizr.system.model.PointType;

/** cityMapX/Y: posicao no mapa da cidade, relativa (0 a 1). */
public record PointRequest(String name, PointType type, String info, Float cityMapX, Float cityMapY) {}

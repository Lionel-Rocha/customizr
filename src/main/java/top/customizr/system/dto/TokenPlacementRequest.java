package top.customizr.system.dto;

import top.customizr.system.model.TokenLayer;

import java.util.List;
import java.util.UUID;

/**
 * Altura, camada e fichas do token, cada campo so muda se vier.
 * elevationFt: o dono tambem muda (voar, subir no telhado); 0 = no chao.
 * layer e memberIds (as fichas que viajam juntas no token, ex.: a caravana): so o mestre.
 */
public record TokenPlacementRequest(Integer elevationFt, TokenLayer layer, List<UUID> memberIds) {}

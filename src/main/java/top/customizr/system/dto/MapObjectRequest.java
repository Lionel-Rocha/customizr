package top.customizr.system.dto;

import top.customizr.system.model.TokenLayer;

import java.util.UUID;

/** Objeto da biblioteca: imageId (de POST /image), nome, tamanho em casas (0,5 a 30) e camada (padrao: cenario). */
public record MapObjectRequest(String name, UUID imageId, Double widthCells, Double heightCells, TokenLayer layer) {}

package top.customizr.system.dto;

import java.util.UUID;

public record TokenRequest(String name, Double x, Double y, Integer sizeCells, String imageUrl, Boolean hidden,
                           UUID characterId, UUID npcId, UUID monsterId, Integer rotation,
                           Integer hpCurrent, Integer hpMax) {}

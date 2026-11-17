package top.customizr.system.dto;

import java.util.UUID;

public record TokenMovedResponse(UUID sceneId, UUID tokenId, double x, double y, Integer distanceFeet) {}

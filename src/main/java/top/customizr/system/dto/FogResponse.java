package top.customizr.system.dto;

import java.util.Set;
import java.util.UUID;

public record FogResponse(UUID sceneId, boolean fogEnabled, Set<String> revealedCells) {}

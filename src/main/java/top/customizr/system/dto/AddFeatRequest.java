package top.customizr.system.dto;

import java.util.UUID;

public record AddFeatRequest(UUID featureId, int selectedAtLevel) {}

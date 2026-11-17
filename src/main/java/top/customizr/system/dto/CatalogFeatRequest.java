package top.customizr.system.dto;

import java.util.UUID;

public record CatalogFeatRequest(UUID featId, int characterLevel, String choices) {}

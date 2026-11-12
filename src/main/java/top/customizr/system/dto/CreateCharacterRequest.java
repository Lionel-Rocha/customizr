package top.customizr.system.dto;

import java.util.UUID;

public record CreateCharacterRequest(UUID campaignId, String name) {}

package top.customizr.system.dto;

import java.util.UUID;

public record LootItemRequest(UUID itemId, Integer quantity) {}

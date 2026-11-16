package top.customizr.system.dto;

import java.util.UUID;

/** actorCharacterId: o personagem (do usuario logado) que esta mexendo no inventario. */
public record InventoryItemRequest(UUID actorCharacterId, UUID itemId, int quantity) {}

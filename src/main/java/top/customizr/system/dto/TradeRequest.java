package top.customizr.system.dto;

import java.util.UUID;

/**
 * Comprar (entryId da loja), vender (itemId do inventario) ou pegar do espolio (entryId), sempre pelo personagem.
 * toParty: o que foi pego vai pro inventario do grupo em vez do personagem.
 */
public record TradeRequest(UUID characterId, UUID entryId, UUID itemId, Integer quantity, Boolean toParty) {}

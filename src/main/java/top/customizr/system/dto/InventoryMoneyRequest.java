package top.customizr.system.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** amount positivo soma, negativo tira (em po). */
public record InventoryMoneyRequest(UUID actorCharacterId, BigDecimal amount) {}

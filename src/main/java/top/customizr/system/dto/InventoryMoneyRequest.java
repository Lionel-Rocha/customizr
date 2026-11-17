package top.customizr.system.dto;

import top.customizr.system.model.Currency;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * amount positivo soma, negativo tira. currency = em qual moeda esta o amount (padrao: ouro);
 * o inventario guarda em po, entao 5 pp vira 0,5 po.
 */
public record InventoryMoneyRequest(UUID actorCharacterId, BigDecimal amount, Currency currency) {
    public InventoryMoneyRequest(UUID actorCharacterId, BigDecimal amount) {
        this(actorCharacterId, amount, null);
    }

    public BigDecimal amountInGold() {
        return amount == null ? null : (currency == null ? Currency.GOLD : currency).toGold(amount);
    }
}

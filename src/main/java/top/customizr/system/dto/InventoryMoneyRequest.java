package top.customizr.system.dto;

import top.customizr.system.model.Currency;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * amount positivo soma, negativo tira. currency = em qual moeda esta o amount (padrao: ouro).
 * Com moeda e quantidade inteira, entram (ou saem) essas moedas na bolsa (37 pp ficam 37 pp); sem moeda, o valor
 * em po entra quebrado em po/pp/pc. Sair mais do que tem de uma moeda paga o resto pelo valor, com troco.
 */
public record InventoryMoneyRequest(UUID actorCharacterId, BigDecimal amount, Currency currency) {
    public InventoryMoneyRequest(UUID actorCharacterId, BigDecimal amount) {
        this(actorCharacterId, amount, null);
    }

    public BigDecimal amountInGold() {
        return amount == null ? null : (currency == null ? Currency.GOLD : currency).toGold(amount);
    }
}

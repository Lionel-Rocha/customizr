package top.customizr.system.dto;

import java.math.BigDecimal;

/** Espolio: nome, moedas (em po) e se os jogadores ja veem. */
public record LootRequest(String name, BigDecimal moneyGp, Boolean visible) {}

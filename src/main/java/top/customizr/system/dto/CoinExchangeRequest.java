package top.customizr.system.dto;

import top.customizr.system.model.Currency;

import java.util.UUID;

/** Cambio no inventario: {@code amount} moedas de {@code from} viram {@code to} (o valor precisa fechar). */
public record CoinExchangeRequest(UUID actorCharacterId, Currency from, Currency to, Long amount) {}

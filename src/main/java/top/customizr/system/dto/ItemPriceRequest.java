package top.customizr.system.dto;

import java.math.BigDecimal;

/** Preco em po; nulo apaga o preco da campanha. */
public record ItemPriceRequest(BigDecimal priceGp) {}

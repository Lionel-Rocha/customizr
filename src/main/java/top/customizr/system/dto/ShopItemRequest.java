package top.customizr.system.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Item a venda: itemId do catalogo, preco em po (vazio = o do catalogo) e estoque (vazio = sem limite). */
public record ShopItemRequest(UUID itemId, BigDecimal priceGp, Integer stock) {}

package top.customizr.system.dto;

import java.math.BigDecimal;

/**
 * Item proprio do mestre. {@code type}: MUNDANE ou MAGICAL; {@code category}: uma das categorias do catalogo
 * (opcional); {@code rarity}: COMMON... ARTIFACT (padrao COMMON); {@code referenceLink}: link ou nota (opcional).
 * Na edicao, campo nulo fica como esta (preco: mande {@code clearPrice} pra apagar).
 */
public record CustomItemRequest(String name, String type, String category, String rarity, BigDecimal priceGp,
                                String referenceLink, Boolean clearPrice) {}

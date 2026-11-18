package top.customizr.system.dto;

/** Loja: nome, descricao, aberta pros jogadores e quanto paga ao comprar deles (% do preco; padrao 50). */
public record ShopRequest(String name, String description, Boolean open, Integer buyBackPercent) {}

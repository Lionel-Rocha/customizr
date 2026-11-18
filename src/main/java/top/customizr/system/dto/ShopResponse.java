package top.customizr.system.dto;

import top.customizr.system.model.Shop;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ShopResponse(UUID id, String name, String description, boolean open, int buyBackPercent, List<Entry> items) {

    public record Entry(UUID entryId, UUID itemId, String name, String type, String rarity, BigDecimal priceGp, Integer stock) {}

    public static ShopResponse from(Shop s) {
        return from(s, Map.of());
    }

    /** campaignPrices: precos que o mestre deu aos itens sem preco oficial. */
    public static ShopResponse from(Shop s, Map<UUID, BigDecimal> campaignPrices) {
        return new ShopResponse(s.getId(), s.getName(), s.getDescription(), s.isOpen(), s.getBuyBackPercent(),
                s.getEntries().stream().map(e -> new Entry(e.getId(), e.getItem().getId(), e.getItem().getName(),
                        e.getItem().getType() == null ? null : e.getItem().getType().name(),
                        e.getItem().getRarity() == null ? null : e.getItem().getRarity().name(),
                        e.price() != null ? e.price() : campaignPrices.get(e.getItem().getId()), e.getStock())).toList());
    }
}

package top.customizr.system.dto;

import top.customizr.system.model.Loot;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LootResponse(UUID id, String name, boolean visible, BigDecimal moneyGp, List<Entry> items) {

    public record Entry(UUID entryId, UUID itemId, String name, String rarity, int quantity) {}

    public static LootResponse from(Loot l) {
        return new LootResponse(l.getId(), l.getName(), l.isVisible(), l.getMoneyGp(),
                l.getEntries().stream().map(e -> new Entry(e.getId(), e.getItem().getId(), e.getItem().getName(),
                        e.getItem().getRarity() == null ? null : e.getItem().getRarity().name(), e.getQuantity())).toList());
    }
}

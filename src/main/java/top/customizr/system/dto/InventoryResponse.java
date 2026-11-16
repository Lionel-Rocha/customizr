package top.customizr.system.dto;

import top.customizr.system.model.Inventory;
import top.customizr.system.model.InventoryEntry;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record InventoryResponse(
        UUID inventoryId,
        OwnerType ownerType,
        UUID ownerId,
        String ownerName,
        BigDecimal moneyGp,
        List<EntryInfo> entries
) {
    public enum OwnerType { CHARACTER, PARTY, NPC }

    public static InventoryResponse from(Inventory inventory) {
        OwnerType type;
        UUID ownerId;
        String ownerName;
        if (inventory.getCharacter() != null) {
            type = OwnerType.CHARACTER;
            ownerId = inventory.getCharacter().getCharacterId();
            ownerName = inventory.getCharacter().getName();
        } else if (inventory.getParty() != null) {
            type = OwnerType.PARTY;
            ownerId = inventory.getParty().getPartyId();
            ownerName = inventory.getParty().getName();
        } else {
            type = OwnerType.NPC;
            ownerId = inventory.getNpc().getId();
            ownerName = inventory.getNpc().getName();
        }
        return new InventoryResponse(
                inventory.getId(),
                type,
                ownerId,
                ownerName,
                inventory.getMoneyGp(),
                inventory.getEntries().stream()
                        .map(EntryInfo::from)
                        .sorted(Comparator.comparing(EntryInfo::name, String.CASE_INSENSITIVE_ORDER))
                        .toList()
        );
    }

    public record EntryInfo(UUID itemId, String name, int quantity, BigDecimal priceGp, String rarity) {
        private static EntryInfo from(InventoryEntry entry) {
            return new EntryInfo(
                    entry.getItem().getId(),
                    entry.getItem().getName(),
                    entry.getQuantity(),
                    entry.getItem().getPriceGp(),
                    entry.getItem().getRarity() == null ? null : entry.getItem().getRarity().name()
            );
        }
    }
}

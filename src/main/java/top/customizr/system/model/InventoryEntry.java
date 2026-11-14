package top.customizr.system.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Column;
import lombok.Getter;

import java.util.UUID;

@Entity
@Table(
        name = "inventory_entries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_entry_item",
                columnNames = {"inventory_id", "item_id"}
        )
)
@Getter
public class InventoryEntry {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private int quantity;

    protected InventoryEntry() {}

    InventoryEntry(Inventory inventory, Item item, int quantity) {
        if (inventory == null || item == null) {
            throw new IllegalArgumentException("Inventory and item are required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.id = UUID.randomUUID();
        this.inventory = inventory;
        this.item = item;
        this.quantity = quantity;
    }

    void changeQuantity(int difference) {
        if (difference > 0 && quantity > Integer.MAX_VALUE - difference) {
            throw new IllegalArgumentException("Quantity exceeds the supported limit");
        }
        int updatedQuantity = Math.addExact(quantity, difference);
        if (updatedQuantity <= 0) {
            throw new IllegalArgumentException("Quantity must remain positive");
        }
        this.quantity = updatedQuantity;
    }
}

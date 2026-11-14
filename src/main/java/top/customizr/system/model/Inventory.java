package top.customizr.system.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "inventory")
@Getter
@EqualsAndHashCode(of = "id")
public class Inventory {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", unique = true)
    private Party party;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", unique = true)
    private Character character;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "npc_id", unique = true)
    private NPC npc;

    @OneToMany(mappedBy = "inventory", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<InventoryEntry> entries = new HashSet<>();

    @Column(name = "money_gp", nullable = false, precision = 12, scale = 2)
    private BigDecimal moneyGp = BigDecimal.ZERO;

    protected Inventory() {}

    public static Inventory forCharacter(Character character) {
        if (character == null) {
            throw new IllegalArgumentException("Character is required");
        }
        Inventory inventory = new Inventory();
        inventory.id = UUID.randomUUID();
        inventory.character = character;
        return inventory;
    }

    public static Inventory forParty(Party party) {
        if (party == null) {
            throw new IllegalArgumentException("Party is required");
        }
        Inventory inventory = new Inventory();
        inventory.id = UUID.randomUUID();
        inventory.party = party;
        return inventory;
    }

    public static Inventory forNpc(NPC npc) {
        if (npc == null) {
            throw new IllegalArgumentException("NPC is required");
        }
        Inventory inventory = new Inventory();
        inventory.id = UUID.randomUUID();
        inventory.npc = npc;
        return inventory;
    }

    public InventoryEntry addItem(Item item, int quantity) {
        if (item == null) {
            throw new IllegalArgumentException("Item is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        InventoryEntry existing = entries.stream()
                .filter(entry -> entry.getItem().getId().equals(item.getId()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            existing.changeQuantity(quantity);
            return existing;
        }
        InventoryEntry entry = new InventoryEntry(this, item, quantity);
        entries.add(entry);
        return entry;
    }

    public void removeItem(UUID itemId, int quantity) {
        if (itemId == null || quantity <= 0) {
            throw new IllegalArgumentException("Item ID and a positive quantity are required");
        }
        InventoryEntry entry = entries.stream()
                .filter(candidate -> candidate.getItem().getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item is not in this inventory"));
        if (quantity > entry.getQuantity()) {
            throw new IllegalArgumentException("Cannot remove more items than the inventory contains");
        }
        if (quantity == entry.getQuantity()) {
            entries.remove(entry);
        } else {
            entry.changeQuantity(-quantity);
        }
    }

    public void changeMoney(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Money amount is required");
        }
        BigDecimal updatedAmount = moneyGp.add(amount);
        if (updatedAmount.signum() < 0) {
            throw new IllegalArgumentException("Inventory money cannot be negative");
        }
        moneyGp = updatedAmount;
    }
}

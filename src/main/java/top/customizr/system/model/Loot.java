package top.customizr.system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Espolio (o bau do dragao, os bolsos do bandido): moedas e itens do catalogo. Visivel = os jogadores veem e pegam;
 * o mestre divide as moedas entre o grupo.
 */
@Entity
@Table(name = "loot")
@Getter
public class Loot {

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Campaign campaign;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private boolean visible;

    @Column(name = "money_gp", nullable = false, precision = 12, scale = 2)
    private BigDecimal moneyGp = BigDecimal.ZERO;

    @OneToMany(mappedBy = "loot", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "position")
    private List<LootEntry> entries = new ArrayList<>();

    protected Loot() {}

    public Loot(Campaign campaign, String name, BigDecimal moneyGp, boolean visible) {
        if (campaign == null) throw new IllegalArgumentException("Campaign is required");
        this.id = UUID.randomUUID();
        this.campaign = campaign;
        update(name, moneyGp, visible);
    }

    public void update(String name, BigDecimal moneyGp, boolean visible) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Loot name is required");
        if (name.trim().length() > 80) throw new IllegalArgumentException("Loot name is too long");
        if (moneyGp != null && moneyGp.signum() < 0) throw new IllegalArgumentException("Money cannot be negative");
        this.name = name.trim();
        if (moneyGp != null) this.moneyGp = moneyGp.setScale(2, RoundingMode.HALF_UP);
        this.visible = visible;
    }

    public LootEntry addItem(Item item, int quantity) {
        if (item == null) throw new IllegalArgumentException("Item is required");
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        for (LootEntry e : entries) {
            if (e.getItem().getId().equals(item.getId())) { e.change(quantity); return e; }
        }
        if (entries.size() >= 300) throw new IllegalArgumentException("Too many items in this loot");
        LootEntry entry = new LootEntry(this, item, quantity);
        entries.add(entry);
        return entry;
    }

    /** Tira do bau (o que zerou sai da lista). */
    public Item take(UUID entryId, int quantity) {
        LootEntry entry = entries.stream().filter(e -> e.getId().equals(entryId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("This item is not in the loot"));
        if (quantity <= 0 || quantity > entry.getQuantity()) {
            throw new IllegalArgumentException("There are only " + entry.getQuantity() + " of " + entry.getItem().getName());
        }
        entry.change(-quantity);
        if (entry.getQuantity() == 0) entries.remove(entry);
        return entry.getItem();
    }

    public void takeMoney(BigDecimal amount) {
        if (amount.compareTo(moneyGp) > 0) throw new IllegalArgumentException("There is not that much money in the loot");
        moneyGp = moneyGp.subtract(amount);
    }
}

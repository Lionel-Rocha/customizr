package top.customizr.system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

@Entity
@Table(name = "loot_entries")
@Getter
public class LootEntry {

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loot_id", nullable = false)
    private Loot loot;

    @ManyToOne(optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Item item;

    @Column(nullable = false)
    private int quantity;

    protected LootEntry() {}

    LootEntry(Loot loot, Item item, int quantity) {
        this.id = UUID.randomUUID();
        this.loot = loot;
        this.item = item;
        this.quantity = quantity;
    }

    void change(int delta) {
        quantity += delta;
    }
}

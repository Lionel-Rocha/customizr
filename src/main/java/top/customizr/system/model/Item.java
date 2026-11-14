package top.customizr.system.model;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;
@Entity
@Table(
        name = "item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_item_name_source",
                columnNames = {"name", "source_id"}
        ),
        indexes = {
                @Index(name = "idx_item_name", columnList = "name"),
                @Index(name = "idx_item_source", columnList = "source_id")
        }
)
@Getter
@EqualsAndHashCode(of = "id")
public class Item {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    /**
     * Uma linha por (item, source). "Longsword" no PHB e "Longsword" no XGE
     * são duas entidades Item distintas, com mesmo `name` e `source` diferente.
     * O "item conceitual" é derivado de `name` nas queries, não modelado como FK.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemType type;

    @Enumerated(EnumType.STRING)
    private ItemCategory category;     // nullable: Potion não tem "Simple/Martial"

    @Column(name = "reference_link")
    private String referenceLink;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rarity rarity;

    @Column(name = "price_gp", precision = 10, scale = 2)
    private BigDecimal priceGp;

    @Column(nullable = false)
    private boolean custom = false;

    protected Item() {}

    public Item(String name, Source source, ItemType type, ItemCategory category,
                String referenceLink, Rarity rarity, BigDecimal priceGp) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name is required");
        }
        if (source == null || type == null || rarity == null) {
            throw new IllegalArgumentException("Item source, type, and rarity are required");
        }
        if (priceGp != null && priceGp.signum() < 0) {
            throw new IllegalArgumentException("Item price cannot be negative");
        }
        this.id = UUID.randomUUID();
        this.name = name.trim();
        this.source = source;
        this.type = type;
        this.category = category;
        this.referenceLink = referenceLink;
        this.rarity = rarity;
        this.priceGp = priceGp;
        this.custom = false;
    }

    public void update(String name, Source source, ItemType type, ItemCategory category,
                       String referenceLink, Rarity rarity, BigDecimal priceGp) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name is required");
        }
        if (source == null || type == null || rarity == null) {
            throw new IllegalArgumentException("Item source, type, and rarity are required");
        }
        if (priceGp != null && priceGp.signum() < 0) {
            throw new IllegalArgumentException("Item price cannot be negative");
        }
        this.name = name.trim();
        this.source = source;
        this.type = type;
        this.category = category;
        this.referenceLink = referenceLink;
        this.rarity = rarity;
        this.priceGp = priceGp;
    }
}

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

    /** Item que o mestre criou pra campanha dele (nulo = catalogo do servidor). */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Campaign campaign;

    protected Item() {}

    /** Item proprio do mestre, na fonte homebrew da campanha. */
    public static Item custom(String name, Source source, Campaign campaign, ItemType type, ItemCategory category,
                              String referenceLink, Rarity rarity, BigDecimal priceGp) {
        if (campaign == null) throw new IllegalArgumentException("Falta informar a campanha");
        Item item = new Item(name, source, type, category, referenceLink, rarity, priceGp);
        item.custom = true;
        item.campaign = campaign;
        return item;
    }

    public Item(String name, Source source, ItemType type, ItemCategory category,
                String referenceLink, Rarity rarity, BigDecimal priceGp) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Falta o nome do item");
        }
        if (source == null || type == null || rarity == null) {
            throw new IllegalArgumentException("Faltam a fonte, o tipo e a raridade do item");
        }
        if (priceGp != null && priceGp.signum() < 0) {
            throw new IllegalArgumentException("O preço do item não pode ser negativo");
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
            throw new IllegalArgumentException("Falta o nome do item");
        }
        if (source == null || type == null || rarity == null) {
            throw new IllegalArgumentException("Faltam a fonte, o tipo e a raridade do item");
        }
        if (priceGp != null && priceGp.signum() < 0) {
            throw new IllegalArgumentException("O preço do item não pode ser negativo");
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

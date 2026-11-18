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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Loja da campanha (ferreiro, alquimista, mercado): itens do catalogo com preco e estoque. Aberta = os jogadores veem
 * e compram. buyBackPercent: quanto a loja paga quando o jogador vende (o livro sugere metade do preco).
 */
@Entity
@Table(name = "shops")
@Getter
public class Shop {

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Campaign campaign;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "is_open", nullable = false)
    private boolean open;

    @Column(name = "buy_back_percent", nullable = false)
    private int buyBackPercent;

    @OneToMany(mappedBy = "shop", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "position")
    private List<ShopEntry> entries = new ArrayList<>();

    protected Shop() {}

    public Shop(Campaign campaign, String name, String description, boolean open, Integer buyBackPercent) {
        if (campaign == null) throw new IllegalArgumentException("Campaign is required");
        this.id = UUID.randomUUID();
        this.campaign = campaign;
        update(name, description, open, buyBackPercent);
    }

    public void update(String name, String description, boolean open, Integer buyBackPercent) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Shop name is required");
        if (name.trim().length() > 80) throw new IllegalArgumentException("Shop name is too long");
        if (description != null && description.length() > 1000) throw new IllegalArgumentException("Description is too long");
        int percent = buyBackPercent == null ? 50 : buyBackPercent;
        if (percent < 0 || percent > 100) throw new IllegalArgumentException("Buy-back goes from 0 to 100%");
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.open = open;
        this.buyBackPercent = percent;
    }

    public ShopEntry addItem(Item item, BigDecimal priceGp, Integer stock) {
        if (entries.size() >= 300) throw new IllegalArgumentException("Too many items in this shop");
        ShopEntry entry = new ShopEntry(this, item, priceGp, stock);
        entries.add(entry);
        return entry;
    }

    public ShopEntry entry(UUID entryId) {
        return entries.stream().filter(e -> e.getId().equals(entryId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("This item is not sold here"));
    }
}

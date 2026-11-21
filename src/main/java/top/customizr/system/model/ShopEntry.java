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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** Item a venda: preco proprio (ou o do catalogo) e estoque (null = sem limite). */
@Entity
@Table(name = "shop_entries")
@Getter
public class ShopEntry {

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Item item;

    @Column(name = "price_gp", precision = 12, scale = 2)
    private BigDecimal priceGp;

    private Integer stock;

    protected ShopEntry() {}

    ShopEntry(Shop shop, Item item, BigDecimal priceGp, Integer stock) {
        if (item == null) throw new IllegalArgumentException("Falta informar o item");
        this.id = UUID.randomUUID();
        this.shop = shop;
        this.item = item;
        update(priceGp, stock);
    }

    public void update(BigDecimal priceGp, Integer stock) {
        if (priceGp != null && priceGp.signum() < 0) throw new IllegalArgumentException("O preço não pode ser negativo");
        if (stock != null && stock < 0) throw new IllegalArgumentException("O estoque não pode ser negativo");
        this.priceGp = priceGp == null ? null : priceGp.setScale(2, RoundingMode.HALF_UP);
        this.stock = stock;
    }

    /** Preco valendo: o da loja, ou o do catalogo. Null = sem preco (nao da pra comprar). */
    public BigDecimal price() {
        return priceGp != null ? priceGp : item.getPriceGp();
    }

    /** Sai do estoque (sem limite = nao conta). */
    public void take(int quantity) {
        if (stock == null) return;
        if (quantity > stock) throw new IllegalArgumentException("Só " + stock + " left in stock");
        stock -= quantity;
    }

    public void restock(int quantity) {
        if (stock != null) stock += quantity;
    }
}

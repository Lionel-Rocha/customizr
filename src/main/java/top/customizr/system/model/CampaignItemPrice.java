package top.customizr.system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Preco que o mestre deu, na campanha dele, a um item do catalogo que nao tem preco oficial. Nao mexe no
 * catalogo (que e de todo o servidor): cada mesa decide o seu.
 */
@Entity
@Table(name = "campaign_item_prices", uniqueConstraints = @UniqueConstraint(
        name = "uk_campaign_item_price", columnNames = {"campaign_id", "item_id"}))
@Getter
public class CampaignItemPrice {

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Item item;

    @Column(name = "price_gp", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceGp;

    protected CampaignItemPrice() {}

    public CampaignItemPrice(Campaign campaign, Item item, BigDecimal priceGp) {
        if (campaign == null || item == null) throw new IllegalArgumentException("Faltam a campanha e o item");
        this.id = UUID.randomUUID();
        this.campaign = campaign;
        this.item = item;
        change(priceGp);
    }

    public void change(BigDecimal priceGp) {
        if (priceGp == null || priceGp.signum() < 0) throw new IllegalArgumentException("O preço precisa ser zero ou mais");
        this.priceGp = priceGp.setScale(2, RoundingMode.HALF_UP);
    }
}

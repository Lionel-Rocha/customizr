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

import java.util.UUID;

/**
 * Icone do pacote da campanha: troca o desenho padrao de uma condicao ("condition:POISONED") ou vira um
 * marcador pronto ("marker:Blessed"). A imagem fica no banco, junto da campanha: pacotes de terceiros nao
 * precisam (nem devem) ir pro repositorio.
 */
@Entity
@Table(name = "campaign_icons", uniqueConstraints = @UniqueConstraint(
        name = "uk_campaign_icons_key", columnNames = {"campaign_id", "icon_key"}))
@Getter
public class CampaignIcon {

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Campaign campaign;

    /** "condition:CODIGO" ou "marker:Nome". */
    @Column(name = "icon_key", nullable = false, length = 80)
    private String key;

    @Column(nullable = false, length = 60)
    private String label;

    @ManyToOne(optional = false)
    @JoinColumn(name = "image_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private ImageAsset image;

    protected CampaignIcon() {}

    public CampaignIcon(Campaign campaign, String key, String label, ImageAsset image) {
        if (campaign == null || image == null) throw new IllegalArgumentException("Campaign and image are required");
        if (!image.getCampaign().getId().equals(campaign.getId())) throw new IllegalArgumentException("Image is from another campaign");
        this.id = UUID.randomUUID();
        this.campaign = campaign;
        this.key = validKey(key);
        replace(label, image);
    }

    public void replace(String label, ImageAsset image) {
        String l = label == null || label.isBlank() ? key.substring(key.indexOf(':') + 1) : label.trim();
        if (l.length() > 60) throw new IllegalArgumentException("Icon label is too long");
        this.label = l;
        this.image = image;
    }

    private static String validKey(String key) {
        if (key == null || !key.matches("(condition|marker):[\\p{L}0-9 _'().-]{1,70}")) {
            throw new IllegalArgumentException("Icon key must be condition:CODE or marker:Name");
        }
        if (key.startsWith("condition:")) {
            String code = key.substring("condition:".length());
            if (!code.equals("EXHAUSTION")) Condition.valueOf(code); // codigo de condicao valido
        }
        return key;
    }
}

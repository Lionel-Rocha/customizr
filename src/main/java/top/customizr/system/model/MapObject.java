package top.customizr.system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * Objeto da biblioteca da campanha (mesa, barril, carroca, arvore...): uma imagem com nome, tamanho padrao em casas
 * e camada. Posto no mapa, vira um token daquela camada (cenario, por padrao) com a imagem e o tamanho.
 */
@Entity
@Table(name = "map_objects")
@Getter
public class MapObject {

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Campaign campaign;

    @Column(nullable = false, length = 80)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "image_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private ImageAsset image;

    @Column(name = "width_cells", nullable = false)
    private double widthCells;

    @Column(name = "height_cells", nullable = false)
    private double heightCells;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TokenLayer layer;

    protected MapObject() {}

    public MapObject(Campaign campaign, String name, ImageAsset image, Double widthCells, Double heightCells, TokenLayer layer) {
        if (campaign == null || image == null) throw new IllegalArgumentException("Faltam a campanha e a imagem");
        if (!image.getCampaign().getId().equals(campaign.getId())) throw new IllegalArgumentException("A imagem é de outra campanha");
        this.id = UUID.randomUUID();
        this.campaign = campaign;
        this.image = image;
        update(name, widthCells, heightCells, layer);
    }

    public void update(String name, Double widthCells, Double heightCells, TokenLayer layer) {
        String n = name == null || name.isBlank() ? "Object" : name.trim();
        if (n.length() > 80) throw new IllegalArgumentException("Nome do objeto muito longo");
        double w = widthCells == null ? 1 : widthCells;
        double h = heightCells == null ? w : heightCells;
        for (double v : new double[] {w, h}) {
            if (Double.isNaN(v) || v < 0.5 || v > 30 || Math.abs(v * 2 - Math.round(v * 2)) > 1e-9) {
                throw new IllegalArgumentException("O tamanho do objeto vai de 0,5 a 30 casas, de 0,5 em 0,5");
            }
        }
        this.name = n;
        this.widthCells = w;
        this.heightCells = h;
        this.layer = layer == null ? TokenLayer.SCENERY : layer;
    }
}

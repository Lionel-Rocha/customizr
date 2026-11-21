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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Mundo da campanha (Campaign 1 -- 0..* World). Coordenadas dos filhos sao relativas ao mapa (0 a 1). */
@Entity
@Table(name = "worlds")
@Getter
public class World {

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false)
    private String name;

    @Column(name = "map_url", length = 1000)
    private String mapUrl;

    @Column(name = "master_info", length = 8000)
    private String masterInfo;

    @OneToMany(mappedBy = "world", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name")
    private List<Country> countries = new ArrayList<>();

    protected World() {}

    public World(String name, String mapUrl, String masterInfo, Campaign campaign) {
        if (campaign == null) {
            throw new IllegalArgumentException("Falta a campanha do mundo");
        }
        update(name, mapUrl, masterInfo);
        this.id = UUID.randomUUID();
        this.campaign = campaign;
    }

    public void update(String name, String mapUrl, String masterInfo) {
        this.name = Places.requireName(name, "mundo");
        this.mapUrl = Places.blankToNull(mapUrl);
        this.masterInfo = Places.blankToNull(masterInfo);
    }

    public Country addCountry(String name, String info, String masterInfo, String mapUrl, Float worldMapX, Float worldMapY) {
        Country country = new Country(this, name, info, masterInfo, mapUrl, worldMapX, worldMapY);
        countries.add(country);
        return country;
    }
}

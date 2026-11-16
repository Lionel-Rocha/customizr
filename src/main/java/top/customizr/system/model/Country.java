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

@Entity
@Table(name = "countries")
@Getter
public class Country implements EncyclopediaReferenceable {

    @Id
    private UUID countryId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_id", nullable = false)
    private World world;

    @Column(nullable = false)
    private String name;

    @Column(length = 8000)
    private String info;

    @Column(name = "master_info", length = 8000)
    private String masterInfo;

    @Column(name = "map_url", length = 1000)
    private String mapUrl;

    @Column(name = "world_map_x")
    private Float worldMapX;

    @Column(name = "world_map_y")
    private Float worldMapY;

    @OneToMany(mappedBy = "country", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name")
    private List<City> cities = new ArrayList<>();

    protected Country() {}

    Country(World world, String name, String info, String masterInfo, String mapUrl, Float worldMapX, Float worldMapY) {
        this.countryId = UUID.randomUUID();
        this.world = world;
        update(name, info, masterInfo, mapUrl, worldMapX, worldMapY);
    }

    public void update(String name, String info, String masterInfo, String mapUrl, Float worldMapX, Float worldMapY) {
        this.name = Places.requireName(name, "Country");
        this.info = Places.blankToNull(info);
        this.masterInfo = Places.blankToNull(masterInfo);
        this.mapUrl = Places.blankToNull(mapUrl);
        this.worldMapX = Places.coordinate(worldMapX, "worldMapX");
        this.worldMapY = Places.coordinate(worldMapY, "worldMapY");
    }

    public City addCity(String name, String info, String cityMapUrl, Float countryMapX, Float countryMapY,
                        Integer population, boolean capital) {
        City city = new City(this, name, info, cityMapUrl, countryMapX, countryMapY, population, capital);
        cities.add(city);
        return city;
    }

    @Override
    public UUID getReferenceId() {
        return countryId;
    }

    @Override
    public String getDisplayName() {
        return name;
    }
}

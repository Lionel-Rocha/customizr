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
@Table(name = "cities")
@Getter
public class City implements EncyclopediaReferenceable {

    @Id
    private UUID cityId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false)
    private Country country;

    @Column(nullable = false)
    private String name;

    @Column(length = 8000)
    private String info;

    @Column(name = "city_map_url", length = 1000)
    private String cityMapUrl;

    @Column(name = "country_map_x")
    private Float countryMapX;

    @Column(name = "country_map_y")
    private Float countryMapY;

    /** Populacao aproximada (vem do Azgaar; usada pro tamanho da cidade no gerador do Watabou). */
    private Integer population;

    @Column(nullable = false)
    private boolean capital;

    @OneToMany(mappedBy = "city", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name")
    private List<Point> points = new ArrayList<>();

    protected City() {}

    City(Country country, String name, String info, String cityMapUrl, Float countryMapX, Float countryMapY,
         Integer population, boolean capital) {
        this.cityId = UUID.randomUUID();
        this.country = country;
        update(name, info, cityMapUrl, countryMapX, countryMapY, population, capital);
    }

    public void update(String name, String info, String cityMapUrl, Float countryMapX, Float countryMapY,
                       Integer population, boolean capital) {
        if (population != null && population < 0) {
            throw new IllegalArgumentException("Population cannot be negative");
        }
        this.name = Places.requireName(name, "City");
        this.info = Places.blankToNull(info);
        this.cityMapUrl = Places.blankToNull(cityMapUrl);
        this.countryMapX = Places.coordinate(countryMapX, "countryMapX");
        this.countryMapY = Places.coordinate(countryMapY, "countryMapY");
        this.population = population;
        this.capital = capital;
    }

    public Point addPoint(String name, PointType type, String info, Float cityMapX, Float cityMapY) {
        Point point = new Point(this, name, type, info, cityMapX, cityMapY);
        points.add(point);
        return point;
    }

    @Override
    public UUID getReferenceId() {
        return cityId;
    }

    @Override
    public String getDisplayName() {
        return name;
    }
}

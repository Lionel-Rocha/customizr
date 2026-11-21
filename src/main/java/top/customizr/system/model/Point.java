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

/** Ponto de interesse da cidade (loja, estalagem, bastiao...). */
@Entity
@Table(name = "points")
@Getter
public class Point implements EncyclopediaReferenceable {

    @Id
    private UUID pointId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointType type;

    @Column(length = 8000)
    private String info;

    @Column(name = "city_map_x")
    private Float cityMapX;

    @Column(name = "city_map_y")
    private Float cityMapY;

    protected Point() {}

    Point(City city, String name, PointType type, String info, Float cityMapX, Float cityMapY) {
        this.pointId = UUID.randomUUID();
        this.city = city;
        update(name, type, info, cityMapX, cityMapY);
    }

    public void update(String name, PointType type, String info, Float cityMapX, Float cityMapY) {
        this.name = Places.requireName(name, "ponto");
        this.type = type == null ? PointType.OTHER : type;
        this.info = Places.blankToNull(info);
        this.cityMapX = Places.coordinate(cityMapX, "cityMapX");
        this.cityMapY = Places.coordinate(cityMapY, "cityMapY");
    }

    @Override
    public UUID getReferenceId() {
        return pointId;
    }

    @Override
    public String getDisplayName() {
        return name;
    }
}

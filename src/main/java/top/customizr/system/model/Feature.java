package top.customizr.system.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

@Entity
@Table(name = "features")
@Getter
public class Feature {

    @Id
    private UUID featureId;

    @Column(nullable = false)
    private String name;

    @Column(length = 4000)
    private String description;

    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeatureType type;

    @ManyToOne
    @JoinColumn(name = "source_id")
    private Source source;

    protected Feature() {}

    public Feature(String name, String description, String category, FeatureType type, Source source) {
        this.featureId = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.category = category;
        this.type = type;
        this.source = source;
    }
}

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "features")
@Getter
public class Feature {

    @Id
    private UUID featureId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeatureType type;

    @ManyToOne
    @JoinColumn(name = "source_id")
    private Source source;

    @Column(name = "source_code")
    private String sourceCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> metadata;

    protected Feature() {}

    public Feature(String name, String description, String category, FeatureType type, Source source) {
        this(name, description, category, type, source, source == null ? null : source.getCode(), null);
    }

    public Feature(
            String name,
            String description,
            String category,
            FeatureType type,
            Source source,
            String sourceCode,
            Map<String, Object> metadata
    ) {
        this.featureId = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.category = category;
        this.type = type;
        this.source = source;
        this.sourceCode = sourceCode;
        this.metadata = metadata;
    }
}

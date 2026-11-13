package top.customizr.system.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "subclasses")
@Getter
public class Subclass {

    @Id
    private UUID subclassId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private DndClass dndClass;

    @Column(nullable = false)
    private String name;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "source_code")
    private String sourceCode;

    @Column(nullable = false)
    private int unlockLevel;

    @ManyToOne
    @JoinColumn(name = "source_id")
    private Source source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> metadata;

    @OneToMany(mappedBy = "subclass", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubclassFeature> features = new ArrayList<>();

    protected Subclass() {}

    Subclass(DndClass dndClass, String name, int unlockLevel, Source source) {
        this(dndClass, name, name, source == null ? null : source.getCode(), unlockLevel, source, null);
    }

    Subclass(
            DndClass dndClass,
            String name,
            String shortName,
            String sourceCode,
            int unlockLevel,
            Source source,
            Map<String, Object> metadata
    ) {
        if (unlockLevel < dndClass.getSubclassUnlockLevel() || unlockLevel > 20) {
            throw new IllegalArgumentException("Subclass unlock level must be within the class levels");
        }
        this.subclassId = UUID.randomUUID();
        this.dndClass = dndClass;
        this.name = name;
        this.shortName = shortName;
        this.sourceCode = sourceCode;
        this.unlockLevel = unlockLevel;
        this.source = source;
        this.metadata = metadata;
    }

    public SubclassFeature addFeature(Feature feature, int level, String choiceGroup) {
        SubclassFeature grant = new SubclassFeature(this, feature, level, choiceGroup);
        features.add(grant);
        return grant;
    }

    public SubclassFeature addFeature(Feature feature, int level) {
        return addFeature(feature, level, null);
    }
}

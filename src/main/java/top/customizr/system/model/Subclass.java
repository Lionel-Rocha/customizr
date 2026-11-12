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

import java.util.ArrayList;
import java.util.List;
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

    @Column(nullable = false)
    private int unlockLevel;

    @ManyToOne
    @JoinColumn(name = "source_id")
    private Source source;

    @OneToMany(mappedBy = "subclass", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubclassFeature> features = new ArrayList<>();

    protected Subclass() {}

    Subclass(DndClass dndClass, String name, int unlockLevel, Source source) {
        if (unlockLevel < dndClass.getSubclassUnlockLevel() || unlockLevel > 20) {
            throw new IllegalArgumentException("Subclass unlock level must be within the class levels");
        }
        this.subclassId = UUID.randomUUID();
        this.dndClass = dndClass;
        this.name = name;
        this.unlockLevel = unlockLevel;
        this.source = source;
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

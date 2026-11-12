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
@Table(name = "dnd_classes")
@Getter
public class DndClass {

    @Id
    private UUID classId;

    @Column(nullable = false)
    private String className;

    @Column(nullable = false)
    private int hitDie;

    @Column(nullable = false)
    private int subclassUnlockLevel;

    @ManyToOne
    @JoinColumn(name = "source_id")
    private Source source;

    @OneToMany(mappedBy = "dndClass", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Subclass> subclasses = new ArrayList<>();

    @OneToMany(mappedBy = "dndClass", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClassFeature> features = new ArrayList<>();

    protected DndClass() {}

    public DndClass(String className, int hitDie, int subclassUnlockLevel, Source source) {
        if (hitDie <= 0) {
            throw new IllegalArgumentException("Hit die must be positive");
        }
        if (subclassUnlockLevel < 1 || subclassUnlockLevel > 20) {
            throw new IllegalArgumentException("Subclass unlock level must be between 1 and 20");
        }
        this.classId = UUID.randomUUID();
        this.className = className;
        this.hitDie = hitDie;
        this.subclassUnlockLevel = subclassUnlockLevel;
        this.source = source;
    }

    public Subclass addSubclass(String name, int unlockLevel, Source source) {
        Subclass subclass = new Subclass(this, name, unlockLevel, source);
        subclasses.add(subclass);
        return subclass;
    }

    public ClassFeature addFeature(Feature feature, int level, String choiceGroup) {
        ClassFeature grant = new ClassFeature(this, feature, level, choiceGroup);
        features.add(grant);
        return grant;
    }

    public ClassFeature addFeature(Feature feature, int level) {
        return addFeature(feature, level, null);
    }
}

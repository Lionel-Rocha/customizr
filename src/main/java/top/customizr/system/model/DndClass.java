package top.customizr.system.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "dnd_classes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"class_name", "source_code"})
})
@Getter
public class DndClass {

    @Id
    private UUID classId;

    @Column(nullable = false)
    private String className;

    @Column(nullable = false)
    private int hitDie;

    @Column(name = "hit_die_number", nullable = false)
    private int hitDieNumber;

    @Column(nullable = false)
    private int subclassUnlockLevel;

    @Column(name = "source_code")
    private String sourceCode;

    private String edition;

    private Integer page;

    private Boolean srd;

    private String spellcastingAbility;

    private String casterProgression;

    @Column(length = 1000)
    private String preparedSpells;

    private String preparedSpellsChange;

    @ManyToOne
    @JoinColumn(name = "source_id")
    private Source source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> metadata;

    @OneToMany(mappedBy = "dndClass", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Subclass> subclasses = new ArrayList<>();

    @OneToMany(mappedBy = "dndClass", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClassFeature> features = new ArrayList<>();

    protected DndClass() {}

    public DndClass(String className, int hitDie, int subclassUnlockLevel, Source source) {
        this(className, source == null ? null : source.getCode(), 1, hitDie, subclassUnlockLevel,
                source, null, null, null, null, null, null, null, null);
    }

    public DndClass(
            String className,
            String sourceCode,
            int hitDieNumber,
            int hitDie,
            int subclassUnlockLevel,
            Source source,
            String edition,
            Integer page,
            Boolean srd,
            String spellcastingAbility,
            String casterProgression,
            String preparedSpells,
            String preparedSpellsChange,
            Map<String, Object> metadata
    ) {
        if (hitDie <= 0) {
            throw new IllegalArgumentException("Hit die must be positive");
        }
        if (hitDieNumber <= 0) {
            throw new IllegalArgumentException("Hit die number must be positive");
        }
        if (subclassUnlockLevel < 1 || subclassUnlockLevel > 20) {
            throw new IllegalArgumentException("Subclass unlock level must be between 1 and 20");
        }
        this.classId = UUID.randomUUID();
        this.className = className;
        this.sourceCode = sourceCode;
        this.hitDieNumber = hitDieNumber;
        this.hitDie = hitDie;
        this.subclassUnlockLevel = subclassUnlockLevel;
        this.source = source;
        this.edition = edition;
        this.page = page;
        this.srd = srd;
        this.spellcastingAbility = spellcastingAbility;
        this.casterProgression = casterProgression;
        this.preparedSpells = preparedSpells;
        this.preparedSpellsChange = preparedSpellsChange;
        this.metadata = metadata;
    }

    public Subclass addSubclass(String name, int unlockLevel, Source source) {
        Subclass subclass = new Subclass(this, name, name, source == null ? null : source.getCode(),
                unlockLevel, source, null);
        subclasses.add(subclass);
        return subclass;
    }

    public Subclass addSubclass(
            String name,
            String shortName,
            String sourceCode,
            int unlockLevel,
            Source source,
            Map<String, Object> metadata
    ) {
        Subclass subclass = new Subclass(this, name, shortName, sourceCode, unlockLevel, source, metadata);
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

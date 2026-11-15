package top.customizr.system.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "spells", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"name", "source_id"})
})
@Getter
@EqualsAndHashCode(of = "spellId")
public class Spell {

    @Id
    @Column(name = "spell_id")
    private UUID spellId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpellSchool school;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpellLevel level;

    @Enumerated(EnumType.STRING)
    @Column(name = "casting_time", nullable = false)
    private Time castingTime;

    @Column(name = "range_ft", nullable = false)
    private int rangeFt;

    @ElementCollection
    @CollectionTable(name = "spell_components", joinColumns = @JoinColumn(name = "spell_id"))
    @Column(name = "component", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Components> components = new LinkedHashSet<>();

    @Embedded
    private Duration duration;

    // Keep the original non-null column populated in existing PostgreSQL schemas.
    @Column(name = "concentration", nullable = false)
    private boolean concentration;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(name = "description", columnDefinition = "text", nullable = false)
    private String description;

    protected Spell() {}

    public Spell(
            String name,
            SpellSchool school,
            SpellLevel level,
            Time castingTime,
            int rangeFt,
            Set<Components> components,
            Duration duration,
            Source source,
            String description
    ) {
        this.spellId = UUID.randomUUID();
        update(name, school, level, castingTime, rangeFt, components, duration, source, description);
    }

    public void update(
            String name,
            SpellSchool school,
            SpellLevel level,
            Time castingTime,
            int rangeFt,
            Set<Components> components,
            Duration duration,
            Source source,
            String description
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Spell name is required");
        }
        if (school == null || level == null || castingTime == null || duration == null || source == null) {
            throw new IllegalArgumentException("Spell school, level, casting time, duration, and source are required");
        }
        if (rangeFt < 0) {
            throw new IllegalArgumentException("Spell range cannot be negative");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Spell description is required");
        }

        this.name = name.trim();
        this.school = school;
        this.level = level;
        this.castingTime = castingTime;
        this.rangeFt = rangeFt;
        this.components.clear();
        if (components != null) this.components.addAll(components);
        this.duration = duration;
        this.concentration = duration.isConcentration();
        this.source = source;
        this.description = description.trim();
    }
}

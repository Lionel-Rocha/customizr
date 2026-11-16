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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "subclass_spells")
@Getter
public class SubclassSpell {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "subclass_id", nullable = false)
    private Subclass subclass;

    @ManyToOne(optional = false)
    @JoinColumn(name = "spell_id", nullable = false)
    private Spell spell;

    @Enumerated(EnumType.STRING)
    @Column(name = "grant_type", nullable = false)
    private SubclassSpellGrantType grantType;

    @Column(name = "granted_at_level")
    private Integer grantedAtLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> metadata;

    protected SubclassSpell() {}

    SubclassSpell(
            Subclass subclass,
            Spell spell,
            SubclassSpellGrantType grantType,
            Integer grantedAtLevel,
            Map<String, Object> metadata
    ) {
        this.id = UUID.randomUUID();
        this.subclass = subclass;
        this.spell = spell;
        this.grantType = grantType;
        this.grantedAtLevel = grantedAtLevel;
        this.metadata = metadata;
    }

    boolean hasImportLocation(int additionalSpellSet, List<String> path) {
        Object storedSet = metadata == null ? null : metadata.get("additionalSpellSet");
        Object storedPath = metadata == null ? null : metadata.get("path");
        return storedSet instanceof Number number
                && number.intValue() == additionalSpellSet
                && storedPath instanceof List<?> storedPathSegments
                && storedPathSegments.equals(path);
    }
}

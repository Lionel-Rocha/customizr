package top.customizr.system.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.util.UUID;

@Entity
@Table(name = "character_feature_selections", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"character_class_id", "feature_id"}),
        @UniqueConstraint(columnNames = {"character_class_id", "choice_group", "offered_at_level"})
})
@Getter
public class CharacterFeatureSelection {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "character_class_id", nullable = false)
    private CharacterClass characterClass;

    @ManyToOne(optional = false)
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    @Column(nullable = false)
    private int level;

    @Column(name = "offered_at_level", nullable = false)
    private int offeredAtLevel;

    @Column(name = "choice_group")
    private String choiceGroup;

    protected CharacterFeatureSelection() {}

    CharacterFeatureSelection(
            CharacterClass characterClass,
            Feature feature,
            int level,
            int offeredAtLevel,
            String choiceGroup
    ) {
        this.id = UUID.randomUUID();
        this.characterClass = characterClass;
        this.feature = feature;
        this.level = level;
        this.offeredAtLevel = offeredAtLevel;
        this.choiceGroup = choiceGroup;
    }
}

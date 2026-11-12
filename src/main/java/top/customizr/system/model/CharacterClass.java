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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "character_classes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"sheet_id", "class_id"})
})
@Getter
public class CharacterClass {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private Sheet sheet;

    @ManyToOne(optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private DndClass dndClass;

    @Column(nullable = false)
    private int level;

    @ManyToOne
    @JoinColumn(name = "subclass_id")
    private Subclass subclass;

    @Column(name = "subclass_chosen_at_level")
    private Integer subclassChosenAtLevel;

    @OneToMany(mappedBy = "characterClass", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CharacterFeatureSelection> featureSelections = new ArrayList<>();

    protected CharacterClass() {}

    CharacterClass(Sheet sheet, DndClass dndClass, int level) {
        validateLevel(level);
        this.id = UUID.randomUUID();
        this.sheet = sheet;
        this.dndClass = dndClass;
        this.level = level;
    }

    public void setLevel(int level) {
        validateLevel(level);
        if (subclassChosenAtLevel != null && level < subclassChosenAtLevel) {
            throw new IllegalArgumentException("Class level cannot be lower than the subclass selection level");
        }
        if (featureSelections.stream().anyMatch(selection -> selection.getLevel() > level)) {
            throw new IllegalArgumentException("Class level cannot be lower than a selected feature's level");
        }
        this.level = level;
    }

    public void chooseSubclass(Subclass subclass, int chosenAtLevel) {
        if (this.subclass != null) {
            throw new IllegalStateException("A subclass has already been chosen for this class");
        }
        if (!dndClass.getClassId().equals(subclass.getDndClass().getClassId())) {
            throw new IllegalArgumentException("Subclass does not belong to this class");
        }
        validateLevel(chosenAtLevel);
        if (chosenAtLevel < subclass.getUnlockLevel()) {
            throw new IllegalArgumentException("Subclass cannot be chosen before its unlock level");
        }
        if (chosenAtLevel > level) {
            throw new IllegalArgumentException("Subclass cannot be chosen above the character's class level");
        }
        this.subclass = subclass;
        this.subclassChosenAtLevel = chosenAtLevel;
    }

    public CharacterFeatureSelection selectFeature(Feature feature, int selectedAtLevel) {
        validateLevel(selectedAtLevel);
        if (selectedAtLevel > level) {
            throw new IllegalArgumentException("Feature cannot be selected above the character's class level");
        }
        if (feature.getType() == FeatureType.AUTOMATIC) {
            throw new IllegalArgumentException("Automatic features are granted by level and cannot be selected");
        }
        if (featureSelections.stream().anyMatch(selection ->
                selection.getFeature().getFeatureId().equals(feature.getFeatureId()))) {
            throw new IllegalArgumentException("Feature has already been selected");
        }

        FeatureOffer offer = findFeatureOffer(feature, selectedAtLevel);
        if (offer == null) {
            throw new IllegalArgumentException("Feature is not offered by this class at the selected level");
        }
        if (feature.getType() == FeatureType.CHOICE
                && (offer.choiceGroup() == null || offer.choiceGroup().isBlank())) {
            throw new IllegalArgumentException("Choice features must belong to a choice group");
        }
        if (offer.choiceGroup() != null && featureSelections.stream().anyMatch(selection ->
                offer.choiceGroup().equals(selection.getChoiceGroup())
                        && offer.level() == selection.getOfferedAtLevel())) {
            throw new IllegalArgumentException("A feature has already been selected from this choice group");
        }

        CharacterFeatureSelection selection = new CharacterFeatureSelection(
                this, feature, selectedAtLevel, offer.level(), offer.choiceGroup());
        featureSelections.add(selection);
        return selection;
    }

    private FeatureOffer findFeatureOffer(Feature feature, int selectedAtLevel) {
        FeatureOffer classFeatureOffer = dndClass.getFeatures().stream()
                .filter(grant -> grant.getFeature().getFeatureId().equals(feature.getFeatureId())
                        && grant.getLevel() <= selectedAtLevel)
                .max(Comparator.comparingInt(ClassFeature::getLevel))
                .map(grant -> new FeatureOffer(grant.getLevel(), grant.getChoiceGroup()))
                .orElse(null);
        if (classFeatureOffer != null) {
            return classFeatureOffer;
        }
        if (subclass == null || selectedAtLevel < subclassChosenAtLevel) {
            return null;
        }
        return subclass.getFeatures().stream()
                .filter(grant -> grant.getFeature().getFeatureId().equals(feature.getFeatureId())
                        && grant.getLevel() <= selectedAtLevel)
                .max(Comparator.comparingInt(SubclassFeature::getLevel))
                .map(grant -> new FeatureOffer(grant.getLevel(), grant.getChoiceGroup()))
                .orElse(null);
    }

    private record FeatureOffer(int level, String choiceGroup) {}

    private void validateLevel(int level) {
        if (level < 1 || level > 20) {
            throw new IllegalArgumentException("Class level must be between 1 and 20");
        }
    }
}

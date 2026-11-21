package top.customizr.system.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
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

    @ManyToMany
    @JoinTable(
            name = "character_class_prepared_spells",
            joinColumns = @JoinColumn(name = "character_class_id"),
            inverseJoinColumns = @JoinColumn(name = "spell_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"character_class_id", "spell_id"})
    )
    private Set<Spell> preparedSpells = new LinkedHashSet<>();

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
            throw new IllegalArgumentException("O nível da classe não pode ficar abaixo do nível em que a subclasse foi escolhida");
        }
        if (featureSelections.stream().anyMatch(selection -> selection.getLevel() > level)) {
            throw new IllegalArgumentException("O nível da classe não pode ficar abaixo do nível de uma característica escolhida");
        }
        this.level = level;
    }

    public void prepareSpell(Spell spell) {
        if (spell == null || !dndClass.getAvailableSpells().contains(spell)) {
            throw new IllegalArgumentException("Essa magia não está disponível pra esta classe");
        }
        if (!preparedSpells.add(spell)) {
            throw new IllegalArgumentException("Essa magia já está preparada");
        }
    }

    public void unprepareSpell(UUID spellId) {
        if (spellId == null || !preparedSpells.removeIf(spell -> spellId.equals(spell.getSpellId()))) {
            throw new IllegalArgumentException("Essa magia não está preparada");
        }
    }

    public void chooseSubclass(Subclass subclass, int chosenAtLevel) {
        if (this.subclass != null) {
            throw new IllegalStateException("Esta classe já tem subclasse escolhida");
        }
        if (!dndClass.getClassId().equals(subclass.getDndClass().getClassId())) {
            throw new IllegalArgumentException("Essa subclasse não é desta classe");
        }
        validateLevel(chosenAtLevel);
        if (chosenAtLevel < subclass.getUnlockLevel()) {
            throw new IllegalArgumentException("A subclasse não pode ser escolhida antes do nível em que ela libera");
        }
        if (chosenAtLevel > level) {
            throw new IllegalArgumentException("A subclasse não pode ser escolhida acima do nível do personagem na classe");
        }
        this.subclass = subclass;
        this.subclassChosenAtLevel = chosenAtLevel;
    }

    public CharacterFeatureSelection selectFeature(Feature feature, int selectedAtLevel) {
        validateLevel(selectedAtLevel);
        if (selectedAtLevel > level) {
            throw new IllegalArgumentException("A característica não pode ser escolhida acima do nível do personagem na classe");
        }
        if (feature.getType() == FeatureType.AUTOMATIC) {
            throw new IllegalArgumentException("Características automáticas vêm com o nível e não precisam ser escolhidas");
        }
        if (featureSelections.stream().anyMatch(selection ->
                selection.getFeature().getFeatureId().equals(feature.getFeatureId()))) {
            throw new IllegalArgumentException("Essa característica já foi escolhida");
        }

        FeatureOffer offer = findFeatureOffer(feature, selectedAtLevel);
        if (offer == null) {
            throw new IllegalArgumentException("Esta classe não oferece essa característica nesse nível");
        }
        if (feature.getType() == FeatureType.CHOICE
                && (offer.choiceGroup() == null || offer.choiceGroup().isBlank())) {
            throw new IllegalArgumentException("Características de escolha precisam pertencer a um grupo de escolha");
        }
        if (offer.choiceGroup() != null && featureSelections.stream().anyMatch(selection ->
                offer.choiceGroup().equals(selection.getChoiceGroup())
                        && offer.level() == selection.getOfferedAtLevel())) {
            throw new IllegalArgumentException("Já foi escolhida uma característica deste grupo");
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
            throw new IllegalArgumentException("O nível da classe vai de 1 a 20");
        }
    }
}

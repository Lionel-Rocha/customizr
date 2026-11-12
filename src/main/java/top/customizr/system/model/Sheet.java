package top.customizr.system.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "character_sheets")
@Getter
public class Sheet {

    @Id
    private UUID sheetId;

    @OneToOne(optional = false)
    @JoinColumn(name = "character_id", nullable = false, unique = true)
    private Character character;

    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CharacterClass> classes = new ArrayList<>();

    protected Sheet() {}

    Sheet(Character character) {
        this.sheetId = UUID.randomUUID();
        this.character = character;
    }

    public CharacterClass addClass(DndClass dndClass, int level) {
        boolean alreadyHasClass = classes.stream()
                .anyMatch(characterClass -> characterClass.getDndClass().getClassId().equals(dndClass.getClassId()));
        if (alreadyHasClass) {
            throw new IllegalArgumentException("Character already has this class");
        }
        CharacterClass characterClass = new CharacterClass(this, dndClass, level);
        classes.add(characterClass);
        return characterClass;
    }
}

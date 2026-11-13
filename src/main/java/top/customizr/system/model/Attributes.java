package top.customizr.system.model;

import jakarta.persistence.Embeddable;
import lombok.Getter;

@Embeddable
@Getter
public class Attributes {
    private int strength;
    private int dexterity;
    private int constitution;
    private int intelligence;
    private int wisdom;
    private int charisma;

    protected Attributes() {}

    public Attributes(int strength, int dexterity, int constitution, int wisdom, int intelligence, int charisma) {
        validate(strength);
        validate(dexterity);
        validate(constitution);
        validate(intelligence);
        validate(wisdom);
        validate(charisma);
        this.strength = strength;
        this.dexterity = dexterity;
        this.constitution = constitution;
        this.intelligence = intelligence;
        this.wisdom = wisdom;
        this.charisma = charisma;
    }

    private void validate(int score) {
        if (score < 1 || score > 30) {
            throw new IllegalArgumentException("Ability scores must be between 1 and 30");
        }
    }
}

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

    // Ordem igual a da ficha (FOR, DES, CON, INT, SAB, CAR). Antes o construtor recebia
    // SAB antes de INT e o CharacterService passava INT antes de SAB: os dois trocavam ao salvar.
    public Attributes(int strength, int dexterity, int constitution, int intelligence, int wisdom, int charisma) {
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

    /** Modificador de 5e: (valor - 10) / 2, arredondando pra baixo (8 = -1, 9 = -1, 10 = 0, 15 = +2). */
    public static int modifierOf(int score) {
        return Math.floorDiv(score - 10, 2);
    }

    /**
     * Modificador pelo nome do atributo, como no diagrama ({@code getModifier(String ability)}).
     * Aceita o nome em ingles ("strength"), a sigla ("STR") ou a sigla em portugues ("FOR").
     */
    public int getModifier(String ability) {
        if (ability == null) {
            throw new IllegalArgumentException("Falta informar o atributo");
        }
        return modifierOf(switch (ability.trim().toLowerCase()) {
            case "strength", "str", "for", "força", "forca" -> strength;
            case "dexterity", "dex", "des", "destreza" -> dexterity;
            case "constitution", "con", "constituição", "constituicao" -> constitution;
            case "intelligence", "int", "inteligência", "inteligencia" -> intelligence;
            case "wisdom", "wis", "sab", "sabedoria" -> wisdom;
            case "charisma", "cha", "car", "carisma" -> charisma;
            default -> throw new IllegalArgumentException("Atributo desconhecido: " + ability);
        });
    }

    private void validate(int score) {
        if (score < 1 || score > 30) {
            throw new IllegalArgumentException("Os valores de atributo vão de 1 a 30");
        }
    }
}

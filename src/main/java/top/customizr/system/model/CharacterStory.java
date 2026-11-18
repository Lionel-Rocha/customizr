package top.customizr.system.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

/**
 * Historia e anotacoes do personagem (texto livre, colunas opcionais). {@code gmNotes}: segredo do mestre sobre o
 * personagem, que o jogador nao ve.
 */
@Embeddable
@Getter
public class CharacterStory {

    public static final int SHORT = 60;
    public static final int LONG = 20000;

    @Column(name = "story_alignment", length = SHORT)
    private String alignment;
    @Column(name = "story_age", length = SHORT)
    private String age;
    @Column(name = "story_height", length = SHORT)
    private String height;
    @Column(name = "story_weight", length = SHORT)
    private String weight;
    @Column(name = "story_eyes", length = SHORT)
    private String eyes;
    @Column(name = "story_hair", length = SHORT)
    private String hair;
    @Column(name = "story_skin", length = SHORT)
    private String skin;

    @Column(name = "story_appearance", columnDefinition = "TEXT")
    private String appearance;
    @Column(name = "story_personality", columnDefinition = "TEXT")
    private String personality;
    @Column(name = "story_ideals", columnDefinition = "TEXT")
    private String ideals;
    @Column(name = "story_bonds", columnDefinition = "TEXT")
    private String bonds;
    @Column(name = "story_flaws", columnDefinition = "TEXT")
    private String flaws;
    @Column(name = "story_backstory", columnDefinition = "TEXT")
    private String backstory;
    @Column(name = "story_allies", columnDefinition = "TEXT")
    private String allies;
    @Column(name = "story_notes", columnDefinition = "TEXT")
    private String notes;
    @Column(name = "story_gm_notes", columnDefinition = "TEXT")
    private String gmNotes;

    public CharacterStory() {}

    /** Campo a campo: nulo = fica como esta; texto vazio = apaga. */
    public void update(String alignment, String age, String height, String weight, String eyes, String hair, String skin,
                       String appearance, String personality, String ideals, String bonds, String flaws,
                       String backstory, String allies, String notes) {
        this.alignment = pick(this.alignment, alignment, SHORT, "alignment");
        this.age = pick(this.age, age, SHORT, "age");
        this.height = pick(this.height, height, SHORT, "height");
        this.weight = pick(this.weight, weight, SHORT, "weight");
        this.eyes = pick(this.eyes, eyes, SHORT, "eyes");
        this.hair = pick(this.hair, hair, SHORT, "hair");
        this.skin = pick(this.skin, skin, SHORT, "skin");
        this.appearance = pick(this.appearance, appearance, LONG, "appearance");
        this.personality = pick(this.personality, personality, LONG, "personality");
        this.ideals = pick(this.ideals, ideals, LONG, "ideals");
        this.bonds = pick(this.bonds, bonds, LONG, "bonds");
        this.flaws = pick(this.flaws, flaws, LONG, "flaws");
        this.backstory = pick(this.backstory, backstory, LONG, "backstory");
        this.allies = pick(this.allies, allies, LONG, "allies");
        this.notes = pick(this.notes, notes, LONG, "notes");
    }

    public void updateGmNotes(String gmNotes) {
        this.gmNotes = pick(this.gmNotes, gmNotes, LONG, "game master notes");
    }

    private static String pick(String current, String incoming, int max, String field) {
        if (incoming == null) return current;
        String value = incoming.strip();
        if (value.length() > max) throw new IllegalArgumentException("The " + field + " text is too long (" + max + " characters)");
        return value.isEmpty() ? null : value;
    }
}

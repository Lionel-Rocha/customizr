package top.customizr.system.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;

/**
 * Ficha de monstro (stat block) do catalogo, importada do bestiario do 5etools. Um NPC da campanha
 * pode usar uma dessas fichas ({@link NPC#getStatBlock()}).
 */
@Entity
@Table(name = "monsters", uniqueConstraints = @UniqueConstraint(
        name = "uk_monsters_name_source", columnNames = {"name", "source_id"}))
@Getter
public class Monster {

    /** XP por ND (tabela do livro). */
    private static final Map<String, Integer> XP_BY_CR = Map.ofEntries(
            Map.entry("0", 10), Map.entry("1/8", 25), Map.entry("1/4", 50), Map.entry("1/2", 100),
            Map.entry("1", 200), Map.entry("2", 450), Map.entry("3", 700), Map.entry("4", 1100),
            Map.entry("5", 1800), Map.entry("6", 2300), Map.entry("7", 2900), Map.entry("8", 3900),
            Map.entry("9", 5000), Map.entry("10", 5900), Map.entry("11", 7200), Map.entry("12", 8400),
            Map.entry("13", 10000), Map.entry("14", 11500), Map.entry("15", 13000), Map.entry("16", 15000),
            Map.entry("17", 18000), Map.entry("18", 20000), Map.entry("19", 22000), Map.entry("20", 25000),
            Map.entry("21", 33000), Map.entry("22", 41000), Map.entry("23", 50000), Map.entry("24", 62000),
            Map.entry("25", 75000), Map.entry("26", 90000), Map.entry("27", 105000), Map.entry("28", 120000),
            Map.entry("29", 135000), Map.entry("30", 155000));

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(length = 40)
    private String size;

    @Column(name = "creature_type", length = 200)
    private String creatureType;

    @Column(length = 100)
    private String alignment;

    @Column(name = "armor_class")
    private Integer armorClass;

    @Column(name = "armor_note", length = 200)
    private String armorNote;

    @Column(name = "hp_average")
    private Integer hpAverage;

    @Column(name = "hp_formula", length = 100)
    private String hpFormula;

    @Column(length = 300)
    private String speed;

    private int strength;
    private int dexterity;
    private int constitution;
    private int intelligence;
    private int wisdom;
    private int charisma;

    @Column(name = "saving_throws", length = 300)
    private String savingThrows;

    @Column(length = 500)
    private String skills;

    @Column(name = "damage_vulnerabilities", length = 500)
    private String damageVulnerabilities;

    @Column(name = "damage_resistances", length = 500)
    private String damageResistances;

    @Column(name = "damage_immunities", length = 500)
    private String damageImmunities;

    @Column(name = "condition_immunities", length = 500)
    private String conditionImmunities;

    @Column(length = 500)
    private String senses;

    @Column(name = "passive_perception")
    private Integer passivePerception;

    @Column(length = 500)
    private String languages;

    /** Nivel de desafio como no livro: "0", "1/8", "1/4", "1/2", "1" ... "30". */
    @Column(name = "challenge_rating", length = 10)
    private String challengeRating;

    @Column(columnDefinition = "TEXT")
    private String traits;

    @Column(columnDefinition = "TEXT")
    private String actions;

    @Column(name = "bonus_actions", columnDefinition = "TEXT")
    private String bonusActions;

    @Column(columnDefinition = "TEXT")
    private String reactions;

    @Column(name = "legendary_actions", columnDefinition = "TEXT")
    private String legendaryActions;

    @Column(columnDefinition = "TEXT")
    private String spellcasting;

    // Mesmo padrao do Feature/DndClass: a entrada original do 5etools (com as tags {@...}) fica em metadata.
    @Column(name = "source_code")
    private String sourceCode;

    private Integer page;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> metadata;

    protected Monster() {}

    public Monster(String name, Source source) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Monster name is required");
        }
        if (source == null) {
            throw new IllegalArgumentException("Monster source is required");
        }
        this.id = UUID.randomUUID();
        this.name = name.trim();
        this.source = source;
    }

    /** Dados basicos. Atributos de 1 a 30, como na ficha de personagem. */
    public void updateCore(String size, String creatureType, String alignment, Integer armorClass, String armorNote,
                           Integer hpAverage, String hpFormula, String speed, int[] abilities, String challengeRating) {
        if (abilities == null || abilities.length != 6) {
            throw new IllegalArgumentException("Six ability scores are required");
        }
        for (int a : abilities) {
            if (a < 1 || a > 30) throw new IllegalArgumentException("Ability scores must be between 1 and 30");
        }
        if (challengeRating != null && !XP_BY_CR.containsKey(challengeRating)) {
            throw new IllegalArgumentException("Unknown challenge rating: " + challengeRating);
        }
        this.size = size;
        this.creatureType = creatureType;
        this.alignment = alignment;
        this.armorClass = armorClass;
        this.armorNote = armorNote;
        this.hpAverage = hpAverage;
        this.hpFormula = hpFormula;
        this.speed = speed;
        this.strength = abilities[0];
        this.dexterity = abilities[1];
        this.constitution = abilities[2];
        this.intelligence = abilities[3];
        this.wisdom = abilities[4];
        this.charisma = abilities[5];
        this.challengeRating = challengeRating;
    }

    public void updateDetails(String savingThrows, String skills, String damageVulnerabilities, String damageResistances,
                              String damageImmunities, String conditionImmunities, String senses,
                              Integer passivePerception, String languages) {
        this.savingThrows = savingThrows;
        this.skills = skills;
        this.damageVulnerabilities = damageVulnerabilities;
        this.damageResistances = damageResistances;
        this.damageImmunities = damageImmunities;
        this.conditionImmunities = conditionImmunities;
        this.senses = senses;
        this.passivePerception = passivePerception;
        this.languages = languages;
    }

    public void updateFeatures(String traits, String actions, String bonusActions, String reactions,
                               String legendaryActions, String spellcasting) {
        this.traits = traits;
        this.actions = actions;
        this.bonusActions = bonusActions;
        this.reactions = reactions;
        this.legendaryActions = legendaryActions;
        this.spellcasting = spellcasting;
    }

    public void updateSourceData(Integer page, Map<String, Object> metadata) {
        this.sourceCode = source.getCode();
        this.page = page;
        this.metadata = metadata;
    }

    public Integer getExperiencePoints() {
        return challengeRating == null ? null : XP_BY_CR.get(challengeRating);
    }

    /** Proficiencia pelo ND: +2 ate ND 4, +3 de 5 a 8 ... +9 no 29-30. */
    public int getProficiencyBonus() {
        if (challengeRating == null || challengeRating.contains("/")) return 2;
        int cr = Integer.parseInt(challengeRating);
        return cr < 5 ? 2 : 2 + (cr - 1) / 4;
    }

    /** ND em numero pra ordenar/filtrar (1/4 = 0.25). */
    public double getChallengeValue() {
        if (challengeRating == null) return 0;
        return switch (challengeRating) {
            case "1/8" -> 0.125;
            case "1/4" -> 0.25;
            case "1/2" -> 0.5;
            default -> Double.parseDouble(challengeRating);
        };
    }
}

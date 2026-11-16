package top.customizr.system.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
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

    @Embedded
    private Attributes attributes;

    // Valores de combate (Integer: fichas antigas no banco ficam com null = ainda nao preenchido)
    @Column(name = "hp_max")
    private Integer hpMax;

    @Column(name = "hp_current")
    private Integer hpCurrent;

    @Column(name = "hp_temp")
    private Integer hpTemp;

    @Column(name = "armor_class")
    private Integer armorClass;

    @Column(name = "speed_ft")
    private Integer speedFt;

    protected Sheet() {}

    public Sheet(Character character) {
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

    @Transient
    public List<SpellSlotCount> getSpellSlots() {
        return SpellSlotCalculator.calculate(classes);
    }

    public void updateAttributes(Attributes attributes) {
        this.attributes = attributes;
    }

    // ------------------------------------------------------------------ valores derivados

    /** Soma dos niveis de todas as classes (multiclasse conta tudo). */
    @Transient
    public int getTotalLevel() {
        return classes.stream().mapToInt(CharacterClass::getLevel).sum();
    }

    /** Bonus de proficiencia pelo nivel total: +2 (1-4), +3 (5-8), +4 (9-12), +5 (13-16), +6 (17-20). */
    @Transient
    public int getProficiencyBonus() {
        int level = Math.max(1, getTotalLevel());
        return 2 + (Math.min(level, 20) - 1) / 4;
    }

    @Transient
    public int getInitiative() {
        return attributes == null ? 0 : attributes.getModifier("dexterity");
    }

    /** Percepcao passiva sem pericias cadastradas ainda: 10 + modificador de SAB. */
    @Transient
    public int getPassivePerception() {
        return 10 + (attributes == null ? 0 : attributes.getModifier("wisdom"));
    }

    /**
     * PV maximo sugerido pelas regras: no 1o nivel da primeira classe, dado cheio + CON; nos outros
     * niveis, a media do dado (metade + 1) + CON. Minimo de 1 PV por nivel.
     * Retorna null se o personagem ainda nao tem classe.
     */
    @Transient
    public Integer getSuggestedHpMax() {
        if (classes.isEmpty()) {
            return null;
        }
        int con = attributes == null ? 0 : attributes.getModifier("constitution");
        int total = 0;
        boolean first = true;
        for (CharacterClass characterClass : classes) {
            int die = Math.max(1, characterClass.getDndClass().getHitDie());
            for (int level = 1; level <= characterClass.getLevel(); level++) {
                int roll = first ? die : die / 2 + 1;
                total += Math.max(1, roll + con);
                first = false;
            }
        }
        return total;
    }

    public void updateCombatStats(Integer hpMax, Integer hpCurrent, Integer hpTemp, Integer armorClass, Integer speedFt) {
        requireNonNegative(hpMax, "Max HP");
        requireNonNegative(hpCurrent, "Current HP");
        requireNonNegative(hpTemp, "Temporary HP");
        requireNonNegative(armorClass, "Armor class");
        requireNonNegative(speedFt, "Speed");
        Integer max = hpMax != null ? hpMax : this.hpMax;
        Integer current = hpCurrent != null ? hpCurrent : this.hpCurrent;
        if (max != null && current != null && current > max) {
            throw new IllegalArgumentException("Current HP cannot exceed max HP");
        }
        if (hpMax != null) this.hpMax = hpMax;
        if (hpCurrent != null) this.hpCurrent = hpCurrent;
        else if (hpMax != null && this.hpCurrent == null) this.hpCurrent = hpMax;
        if (hpTemp != null) this.hpTemp = hpTemp;
        if (armorClass != null) this.armorClass = armorClass;
        if (speedFt != null) this.speedFt = speedFt;
    }

    /** Dano: gasta o PV temporario primeiro, o PV atual nao fica abaixo de 0. */
    public void applyDamage(int amount) {
        requireHp();
        if (amount < 0) {
            throw new IllegalArgumentException("Damage must be positive");
        }
        int temp = hpTemp == null ? 0 : hpTemp;
        int absorbed = Math.min(temp, amount);
        hpTemp = temp - absorbed;
        hpCurrent = Math.max(0, hpCurrent - (amount - absorbed));
    }

    /** Cura: nao passa do PV maximo. */
    public void heal(int amount) {
        requireHp();
        if (amount < 0) {
            throw new IllegalArgumentException("Healing must be positive");
        }
        hpCurrent = Math.min(hpMax, hpCurrent + amount);
    }

    /** PV temporario nao acumula: fica o maior entre o atual e o novo. */
    public void grantTemporaryHp(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Temporary HP must be positive");
        }
        hpTemp = Math.max(hpTemp == null ? 0 : hpTemp, amount);
    }

    private void requireHp() {
        if (hpMax == null || hpCurrent == null) {
            throw new IllegalStateException("Set the character's max HP first");
        }
    }

    private static void requireNonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
    }
}

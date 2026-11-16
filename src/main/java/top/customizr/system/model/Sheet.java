package top.customizr.system.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OrderBy;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
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

    // ------------------------------------------------------------------ ficha completa

    @ElementCollection
    @CollectionTable(name = "sheet_skill_proficiencies", joinColumns = @JoinColumn(name = "sheet_id"))
    @Column(name = "skill", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Skill> skillProficiencies = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "sheet_skill_expertise", joinColumns = @JoinColumn(name = "sheet_id"))
    @Column(name = "skill", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Skill> skillExpertise = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "sheet_saving_throws", joinColumns = @JoinColumn(name = "sheet_id"))
    @Column(name = "ability", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<AbilityScore> savingThrowProficiencies = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "sheet_conditions", joinColumns = @JoinColumn(name = "sheet_id"))
    @Column(name = "condition_name", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Condition> conditions = new LinkedHashSet<>();

    private Integer exhaustion;

    @Column(name = "death_save_successes")
    private Integer deathSaveSuccesses;

    @Column(name = "death_save_failures")
    private Integer deathSaveFailures;

    @Column(name = "hit_dice_spent")
    private Integer hitDiceSpent;

    /** Espacos de magia gastos por nivel (os totais vem do {@link SpellSlotCalculator}). */
    @ElementCollection
    @CollectionTable(name = "sheet_spent_slots", joinColumns = @JoinColumn(name = "sheet_id"))
    @MapKeyColumn(name = "spell_level")
    @Column(name = "spent", nullable = false)
    private Map<Integer, Integer> spentSpellSlots = new HashMap<>();

    @Column(name = "pact_slots_spent")
    private Integer pactSlotsSpent;

    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name")
    private List<SheetAttack> attacks = new ArrayList<>();

    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("characterLevel")
    private List<CharacterFeat> catalogFeats = new ArrayList<>();

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

    /** Percepcao passiva: 10 + bonus de Percepcao (com proficiencia/especializacao). */
    @Transient
    public int getPassivePerception() {
        return 10 + skillBonus(Skill.PERCEPTION);
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
        boolean wasDown = hpCurrent == 0;
        hpCurrent = Math.min(hpMax, hpCurrent + amount);
        if (wasDown && hpCurrent > 0) {
            resetDeathSaves();
            character.revive();
        }
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

    // ------------------------------------------------------------------ pericias e salvaguardas

    public int abilityModifier(AbilityScore ability) {
        return attributes == null ? 0 : attributes.getModifier(ability.getKey());
    }

    public int skillBonus(Skill skill) {
        int bonus = abilityModifier(skill.getAbility());
        if (skillExpertise.contains(skill)) bonus += 2 * getProficiencyBonus();
        else if (skillProficiencies.contains(skill)) bonus += getProficiencyBonus();
        return bonus;
    }

    public int savingThrowBonus(AbilityScore ability) {
        return abilityModifier(ability) + (savingThrowProficiencies.contains(ability) ? getProficiencyBonus() : 0);
    }

    /** Especializacao exige proficiencia na mesma pericia. */
    public void updateProficiencies(Set<Skill> skills, Set<Skill> expertise, Set<AbilityScore> savingThrows) {
        Set<Skill> proficient = skills == null ? EnumSet.noneOf(Skill.class) : EnumSet.copyOf(nonEmpty(skills, Skill.class));
        Set<Skill> experts = expertise == null ? EnumSet.noneOf(Skill.class) : EnumSet.copyOf(nonEmpty(expertise, Skill.class));
        if (!proficient.containsAll(experts)) {
            throw new IllegalArgumentException("Expertise requires proficiency in the same skill");
        }
        skillProficiencies.clear();
        skillProficiencies.addAll(proficient);
        skillExpertise.clear();
        skillExpertise.addAll(experts);
        savingThrowProficiencies.clear();
        if (savingThrows != null) savingThrowProficiencies.addAll(savingThrows);
    }

    private static <E extends Enum<E>> Set<E> nonEmpty(Set<E> set, Class<E> type) {
        return set.isEmpty() ? EnumSet.noneOf(type) : set;
    }

    // ------------------------------------------------------------------ condicoes e exaustao

    public int getExhaustion() {
        return exhaustion == null ? 0 : exhaustion;
    }

    /** Regra de 2024: cada nivel de exaustao tira 2 dos testes de d20 e 5 pes do deslocamento; 6 = morte. */
    public void updateConditions(Set<Condition> conditions, Integer exhaustion) {
        if (exhaustion != null && (exhaustion < 0 || exhaustion > 6)) {
            throw new IllegalArgumentException("Exhaustion must be between 0 and 6");
        }
        if (conditions != null) {
            this.conditions.clear();
            this.conditions.addAll(conditions);
        }
        if (exhaustion != null) {
            this.exhaustion = exhaustion;
            if (exhaustion == 6) character.die();
        }
    }

    @Transient
    public int getD20Penalty() {
        return 2 * getExhaustion();
    }

    @Transient
    public Integer getCurrentSpeedFt() {
        return speedFt == null ? null : Math.max(0, speedFt - 5 * getExhaustion());
    }

    // ------------------------------------------------------------------ testes contra a morte

    public int getDeathSaveSuccesses() {
        return deathSaveSuccesses == null ? 0 : deathSaveSuccesses;
    }

    public int getDeathSaveFailures() {
        return deathSaveFailures == null ? 0 : deathSaveFailures;
    }

    public enum DeathSaveOutcome { SUCCESS, FAILURE, STABLE, DEAD, REVIVED }

    /**
     * Teste contra a morte (so com 0 PV): 10+ sucesso; 1 = duas falhas; 20 = volta com 1 PV.
     * Tres sucessos = estavel; tres falhas = morto.
     */
    public DeathSaveOutcome deathSave(int naturalD20) {
        if (hpCurrent == null || hpCurrent > 0) {
            throw new IllegalStateException("Death saves are only rolled at 0 hit points");
        }
        if (getDeathSaveFailures() >= 3 || getDeathSaveSuccesses() >= 3) {
            throw new IllegalStateException("The character is already stable or dead");
        }
        if (naturalD20 == 20) {
            heal(1);
            return DeathSaveOutcome.REVIVED;
        }
        if (naturalD20 >= 10) {
            deathSaveSuccesses = getDeathSaveSuccesses() + 1;
            return deathSaveSuccesses >= 3 ? DeathSaveOutcome.STABLE : DeathSaveOutcome.SUCCESS;
        }
        deathSaveFailures = Math.min(3, getDeathSaveFailures() + (naturalD20 == 1 ? 2 : 1));
        if (deathSaveFailures >= 3) {
            character.die();
            return DeathSaveOutcome.DEAD;
        }
        return DeathSaveOutcome.FAILURE;
    }

    private void resetDeathSaves() {
        deathSaveSuccesses = 0;
        deathSaveFailures = 0;
    }

    // ------------------------------------------------------------------ espacos de magia

    public int spentSlots(SpellSlotPool pool, int spellLevel) {
        if (pool == SpellSlotPool.PACT) return pactSlotsSpent == null ? 0 : pactSlotsSpent;
        return spentSpellSlots.getOrDefault(spellLevel, 0);
    }

    public void spendSpellSlot(SpellSlotPool pool, int spellLevel) {
        SpellSlotCount slot = getSpellSlots().stream()
                .filter(s -> s.pool() == pool && s.spellLevel() == spellLevel)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The character has no level " + spellLevel + " "
                        + pool.name().toLowerCase() + " slots"));
        int spent = spentSlots(pool, spellLevel);
        if (spent >= slot.totalSlots()) {
            throw new IllegalStateException("No level " + spellLevel + " slots left");
        }
        if (pool == SpellSlotPool.PACT) pactSlotsSpent = spent + 1;
        else spentSpellSlots.put(spellLevel, spent + 1);
    }

    // ------------------------------------------------------------------ dados de vida e descanso

    public int getHitDiceSpent() {
        return hitDiceSpent == null ? 0 : hitDiceSpent;
    }

    @Transient
    public int getHitDiceRemaining() {
        return Math.max(0, getTotalLevel() - getHitDiceSpent());
    }

    /** Resultado do descanso curto: dados gastos (com o valor rolado) e PV recuperado. */
    public record ShortRestResult(List<String> rolls, int healed) {}

    /**
     * Descanso curto: gasta dados de vida (o maior primeiro), cada um cura dado + CON (minimo 0),
     * e recupera os espacos de Magia de Pacto.
     */
    public ShortRestResult shortRest(int hitDice, IntUnaryOperator die) {
        if (hitDice < 0 || hitDice > getHitDiceRemaining()) {
            throw new IllegalArgumentException("Only " + getHitDiceRemaining() + " hit dice left");
        }
        if (hitDice > 0) requireHp();
        List<Integer> pool = new ArrayList<>();
        for (CharacterClass c : classes) {
            for (int i = 0; i < c.getLevel(); i++) pool.add(Math.max(1, c.getDndClass().getHitDie()));
        }
        pool.sort(java.util.Comparator.reverseOrder());
        List<Integer> available = pool.subList(Math.min(pool.size(), getHitDiceSpent()), pool.size());
        int con = abilityModifier(AbilityScore.CONSTITUTION);
        List<String> rolls = new ArrayList<>();
        int before = hpCurrent == null ? 0 : hpCurrent;
        for (int i = 0; i < hitDice; i++) {
            int sides = available.get(i);
            int rolled = die.applyAsInt(sides);
            rolls.add("1d" + sides + "[" + rolled + "]" + (con >= 0 ? "+" : "") + con);
            heal(Math.max(0, rolled + con));
        }
        hitDiceSpent = getHitDiceSpent() + hitDice;
        pactSlotsSpent = 0;
        return new ShortRestResult(rolls, (hpCurrent == null ? 0 : hpCurrent) - before);
    }

    /**
     * Descanso longo: PV cheio, PV temporario zera, recupera todos os espacos de magia e metade dos
     * dados de vida (minimo 1), tira 1 de exaustao e zera os testes contra a morte.
     */
    public void longRest() {
        if (hpMax != null) {
            hpCurrent = hpMax;
            character.revive();
        }
        hpTemp = 0;
        spentSpellSlots.clear();
        pactSlotsSpent = 0;
        hitDiceSpent = Math.max(0, getHitDiceSpent() - Math.max(1, getTotalLevel() / 2));
        if (getExhaustion() > 0) exhaustion = getExhaustion() - 1;
        resetDeathSaves();
    }

    // ------------------------------------------------------------------ ataques e talentos

    public SheetAttack addAttack(String name, int attackBonus, String damage, String damageType, String notes) {
        SheetAttack attack = new SheetAttack(this, name, attackBonus, damage, damageType, notes);
        attacks.add(attack);
        return attack;
    }

    public void removeAttack(UUID attackId) {
        if (!attacks.removeIf(a -> a.getId().equals(attackId))) {
            throw new IllegalArgumentException("Attack not found");
        }
    }

    public CharacterFeat addCatalogFeat(FeatDefinition feat, int characterLevel, String choices) {
        if (catalogFeats.stream().anyMatch(f -> f.getFeat().getId().equals(feat.getId())) && !feat.isRepeatable()) {
            throw new IllegalArgumentException("The character already has this feat");
        }
        CharacterFeat selection = new CharacterFeat(this, feat, characterLevel, choices);
        catalogFeats.add(selection);
        return selection;
    }

    public void removeCatalogFeat(UUID characterFeatId) {
        if (!catalogFeats.removeIf(f -> f.getId().equals(characterFeatId))) {
            throw new IllegalArgumentException("Feat not found on this character");
        }
    }
}
